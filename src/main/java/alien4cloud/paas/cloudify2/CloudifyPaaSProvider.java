package alien4cloud.paas.cloudify2;

import java.util.Map;
import java.util.Set;

import org.cloudifysource.restclient.exceptions.RestClientException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.CloudResourceType;
import alien4cloud.paas.exception.PluginConfigurationException;
import alien4cloud.paas.model.PaaSComputeTemplate;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;

import com.google.common.collect.Sets;

@Component("cloudify-paas-provider-bean")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class CloudifyPaaSProvider extends AbstractCloudifyPaaSProvider {

    private PluginConfigurationBean configurationBean = new PluginConfigurationBean();

    private Map<String, CloudifyComputeTemplate> templates;

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
            this.templates = this.cloudifyRestClientManager.getRestClient().getCloudifyComputeTemplates();
        } catch (RestClientException e) {
            throw new PluginConfigurationException("Unable to retrieve compute templates from cloudify: " + e.getMessageFormattedText(), e);
        }
        configureDefault();
    }

    @Override
    public void updateMatcherConfig(CloudResourceMatcherConfig cloudResourceMatcherConfig) {
        this.recipeGenerator.getPaaSResourceMatcher().configure(cloudResourceMatcherConfig, templates);
    }

    @Override
    public String[] getAvailableResourceIds(CloudResourceType resourceType) {
        if (this.templates == null || this.templates.isEmpty()) {
            return null;
        }
        switch (resourceType) {
        case IMAGE:
            Set<String> imageIds = Sets.newHashSet();
            for (CloudifyComputeTemplate template : templates.values()) {
                imageIds.add(template.getImageId());
            }
            return imageIds.toArray(new String[imageIds.size()]);
        case FLAVOR:
            Set<String> flavorIds = Sets.newHashSet();
            for (CloudifyComputeTemplate template : templates.values()) {
                flavorIds.add(template.getHardwareId());
            }
            return flavorIds.toArray(new String[flavorIds.size()]);
        default:
            return null;
        }
    }

    @Override
    public String[] getAvailableResourceIds(CloudResourceType resourceType, String imageId) {
        if (this.templates == null || this.templates.isEmpty()) {
            return null;
        }
        switch (resourceType) {
        case IMAGE:
            Set<String> flavorIds = Sets.newHashSet();
            for (CloudifyComputeTemplate template : templates.values()) {
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
        PaaSComputeTemplate[] paaSComputeTemplates = new PaaSComputeTemplate[this.templates.size()];
        int i = 0;
        for (Map.Entry<String, CloudifyComputeTemplate> templateEntry : this.templates.entrySet()) {
            paaSComputeTemplates[i++] = new PaaSComputeTemplate(templateEntry.getValue().getImageId(), templateEntry.getValue().getHardwareId(),
                    templateEntry.getKey());
        }
        return paaSComputeTemplates;
    }

}