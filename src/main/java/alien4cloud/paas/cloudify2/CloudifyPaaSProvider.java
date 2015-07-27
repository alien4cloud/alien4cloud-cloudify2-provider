package alien4cloud.paas.cloudify2;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.cloudifysource.restclient.exceptions.RestClientException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import alien4cloud.model.cloud.AvailabilityZone;
import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.CloudResourceType;
import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.model.cloud.HighAvailabilityComputeTemplate;
import alien4cloud.paas.cloudify2.utils.CloudifyPaaSUtils;
import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.model.PaaSComputeTemplate;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

@Slf4j
@Component("cloudify-paas-provider-bean")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class CloudifyPaaSProvider extends AbstractCloudifyPaaSProvider {

    private PluginConfigurationBean configurationBean = new PluginConfigurationBean();

    /* Templates without availability zones */
    private Map<String, CloudifyComputeTemplate> basicTemplates = Maps.newHashMap();
    /* Templates with AZ */
    private Map<String, GeneratedCloudifyComputeTemplate> haTemplates = Maps.newHashMap();
    /* raw templates, json parsed from cloudify getTemplates API */
    private Map<String, Object> rawTemplates = Maps.newHashMap();

    @Override
    protected PluginConfigurationBean getPluginConfigurationBean() {
        return configurationBean;
    }

    @Override
    public void setConfiguration(PluginConfigurationBean configuration) throws PluginConfigurationException {
        this.configurationBean = configuration;
        // tryCloudifyConfigurations(configuration.getCloudifyConnectionConfiguration());
        this.cloudifyRestClientManager.setCloudifyConnectionConfiguration(configuration);
        try {
            Map<String, Object> raw = this.cloudifyRestClientManager.getRestClient().getRawCloudifyTemplates();
            if (raw != null) {
                rawTemplates = raw;
            }
            Map<String, CloudifyComputeTemplate> templates = this.cloudifyRestClientManager.getRestClient().buildCloudifyComputeTemplates(rawTemplates);
            for (Entry<String, CloudifyComputeTemplate> templateEntry : templates.entrySet()) {
                if (templateEntry.getValue() instanceof GeneratedCloudifyComputeTemplate) {
                    this.haTemplates.put(templateEntry.getKey(), (GeneratedCloudifyComputeTemplate) templateEntry.getValue());
                } else {
                    this.basicTemplates.put(templateEntry.getKey(), templateEntry.getValue());
                }
            }
        } catch (RestClientException e) {
            throw new PluginConfigurationException("Unable to retrieve compute templates from cloudify: " + e.getMessageFormattedText(), e);
        }
        configureDefault();
    }

    @Override
    public void updateMatcherConfig(CloudResourceMatcherConfig cloudResourceMatcherConfig) {
        this.recipeGenerator.getPaaSResourceMatcher().configure(cloudResourceMatcherConfig, basicTemplates);
        configureHAComputeTemplates(cloudResourceMatcherConfig);
    }

    @Override
    public String[] getAvailableResourceIds(CloudResourceType resourceType) {
        if (this.basicTemplates == null || this.basicTemplates.isEmpty()) {
            return null;
        }
        switch (resourceType) {
        case IMAGE:
            Set<String> imageIds = Sets.newHashSet();
            for (CloudifyComputeTemplate template : basicTemplates.values()) {
                imageIds.add(template.getImageId());
            }
            return imageIds.toArray(new String[imageIds.size()]);
        case FLAVOR:
            Set<String> flavorIds = Sets.newHashSet();
            for (CloudifyComputeTemplate template : basicTemplates.values()) {
                flavorIds.add(template.getHardwareId());
            }
            return flavorIds.toArray(new String[flavorIds.size()]);
        default:
            return null;
        }
    }

    @Override
    public String[] getAvailableResourceIds(CloudResourceType resourceType, String imageId) {
        if (this.basicTemplates == null || this.basicTemplates.isEmpty()) {
            return null;
        }
        switch (resourceType) {
        case IMAGE:
            Set<String> flavorIds = Sets.newHashSet();
            for (CloudifyComputeTemplate template : basicTemplates.values()) {
                if (imageId.equals(template.getImageId())) {
                    flavorIds.add(template.getHardwareId());
                }
            }
            return flavorIds.toArray(new String[flavorIds.size()]);
        default:
            return null;
        }
    }

    @Override
    public PaaSComputeTemplate[] getAvailablePaaSComputeTemplates() {
        PaaSComputeTemplate[] paaSComputeTemplates = new PaaSComputeTemplate[this.basicTemplates.size()];
        int i = 0;
        for (Map.Entry<String, CloudifyComputeTemplate> templateEntry : this.basicTemplates.entrySet()) {
            paaSComputeTemplates[i++] = new PaaSComputeTemplate(templateEntry.getValue().getImageId(), templateEntry.getValue().getHardwareId(),
                    templateEntry.getKey());
        }
        return paaSComputeTemplates;
    }

    /**
     * for every mapped templates and configure HA equiv given a set of AZ
     *
     * @param config
     */
    private void configureHAComputeTemplates(CloudResourceMatcherConfig config) {
        Map<ComputeTemplate, String> alienTemplateToCloudifyTemplateMapping = this.recipeGenerator.getPaaSResourceMatcher()
                .getAlienTemplateToCloudifyTemplateMapping();

        Map<String, List<String>> missing = Maps.newHashMap();
        // check and configure the HA equiv for every mapped ComputeTemplate
        // FIXME: generate them if not defined
        for (Entry<ComputeTemplate, String> mappedTemplate : alienTemplateToCloudifyTemplateMapping.entrySet()) {
            CloudifyComputeTemplate cloudifyComputeTemplate = basicTemplates.get(mappedTemplate.getValue());
            configureHAComputeTemplates(config, mappedTemplate.getValue(), mappedTemplate.getKey(), cloudifyComputeTemplate, missing);
        }

        for (Entry<String, List<String>> missingEntry : missing.entrySet()) {
            log.warn("HA templates not found for: Template <" + missingEntry.getKey() + ">, AvailabilityZones " + missingEntry.getValue());
        }
    }

    /**
     * given a basic compute template and a set of AZ, configure HA compute templates
     *
     * @param config
     * @param basicPaaSResourceId
     * @param basicTemplate
     * @param cloudifyComputeTemplate
     */

    private void configureHAComputeTemplates(CloudResourceMatcherConfig config, String basicPaaSResourceId, ComputeTemplate basicTemplate,
            CloudifyComputeTemplate cloudifyComputeTemplate, Map<String, List<String>> missingHATemplates) {

        Map<HighAvailabilityComputeTemplate, String> alienTemplateToCloudifyHATemplateMapping = this.recipeGenerator.getPaaSResourceMatcher()
                .getAlienTemplateToCloudifyHATemplateMapping();
        List<String> missing = Lists.newArrayList();
        for (Entry<AvailabilityZone, String> aZEntry : config.getAvailabilityZoneMapping().entrySet()) {
            String haPaaSResourceId = CloudifyPaaSUtils.buildHAPaaSResourceId(basicPaaSResourceId, aZEntry.getValue());
            if (!haTemplates.containsKey(haPaaSResourceId)) {
                missing.add(aZEntry.getValue());
                continue;
            }
            HighAvailabilityComputeTemplate haTemplate = new HighAvailabilityComputeTemplate(basicTemplate, aZEntry.getKey().getId());
            alienTemplateToCloudifyHATemplateMapping.put(haTemplate, haPaaSResourceId);
        }

        if (!missing.isEmpty()) {
            missingHATemplates.put(basicPaaSResourceId, missing);
        }
    }

    public void destroy() {
        this.cloudifyRestClientManager.destroy();
    }
}