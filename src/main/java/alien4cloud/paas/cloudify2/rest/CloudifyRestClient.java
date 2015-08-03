package alien4cloud.paas.cloudify2.rest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.cloudifysource.dsl.rest.response.Response;
import org.cloudifysource.dsl.rest.response.ServiceInstanceDetails;
import org.cloudifysource.restclient.exceptions.RestClientException;
import org.codehaus.jackson.type.TypeReference;

import alien4cloud.paas.cloudify2.AlienExtentedConstants;
import alien4cloud.paas.cloudify2.CloudifyComputeTemplate;
import alien4cloud.paas.cloudify2.GeneratedCloudifyComputeTemplate;
import alien4cloud.paas.cloudify2.rest.external.RestClient;
import alien4cloud.paas.cloudify2.utils.CloudifyPaaSUtils;
import alien4cloud.utils.MapUtil;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Slf4j
public class CloudifyRestClient extends RestClient {

    private static final String GET_SERVICE_INSTANCE_DETAILS_URL_FORMAT = "/%s/service/%s/instances/%s/metadata";
    private static final String GET_SERVICE_INSTANCE_ATTRIBUTES_BIS_URL_FORMAT = "/%s/service/%s/instances/%s/attributes";
    private static final String DEPLOYMENT_CONTROLLER_URL = "/deployments";

    private String versionedDeploymentControllerUrl;

    public CloudifyRestClient(URL url, String username, String password, String apiVersion) throws RestClientException {
        super(url, username, password, apiVersion);
        versionedDeploymentControllerUrl = apiVersion + DEPLOYMENT_CONTROLLER_URL;
    }

    public ServiceInstanceDetails getServiceInstanceDetails(final String appName, final String serviceName, final Integer instanceId)
            throws RestClientException {
        final String url = getFormattedUrl(versionedDeploymentControllerUrl, GET_SERVICE_INSTANCE_DETAILS_URL_FORMAT, appName, serviceName,
                instanceId == null ? "0" : instanceId.toString());
        log.info("[getServiceInstanceDetails] - sending GET request to REST [" + url + "]");
        return executor.get(url, new TypeReference<Response<ServiceInstanceDetails>>() {
        });
    }

    @SuppressWarnings("unchecked")
    public Map<String, CloudifyComputeTemplate> buildCloudifyComputeTemplates(Map<String, Object> rawTemplates) throws RestClientException {
        Map<String, CloudifyComputeTemplate> computeTemplates = Maps.newHashMap();
        if (rawTemplates == null) {
            return computeTemplates;
        }
        List<Object> alreadyAdded = Lists.newArrayList();
        for (Map.Entry<String, Object> templateEntry : rawTemplates.entrySet()) {
            String imageId = null;
            String hardwareId = null;
            String paaSResourceId = templateEntry.getKey();
            CloudifyComputeTemplate cdfyComputeTemplate = null;
            // HA Templates
            if (CloudifyPaaSUtils.HA_TEMPLATE_PAAS_ID_PATTERN.matcher(paaSResourceId).matches()) {
                imageId = (String) MapUtil.get((Map<String, Object>) templateEntry.getValue(), "imageId");
                hardwareId = (String) MapUtil.get((Map<String, Object>) templateEntry.getValue(), "hardwareId");
                String availabilityZone = CloudifyPaaSUtils.getAvailabilityZone((Map<String, Object>) templateEntry.getValue());
                cdfyComputeTemplate = new GeneratedCloudifyComputeTemplate(imageId, hardwareId, availabilityZone);
            } else if (isTemplateEligible(templateEntry.getValue(), alreadyAdded)) {
                imageId = (String) MapUtil.get((Map<String, Object>) templateEntry.getValue(), "imageId");
                hardwareId = (String) MapUtil.get((Map<String, Object>) templateEntry.getValue(), "hardwareId");
                cdfyComputeTemplate = new CloudifyComputeTemplate(imageId, hardwareId);
            } else {
                log.warn("Template declaration [" + templateEntry.getKey() + "] is similar to one already parsed template. Will ignore it");
            }

            if (StringUtils.isNoneBlank(imageId, hardwareId)) {
                computeTemplates.put(paaSResourceId, cdfyComputeTemplate);
            }
        }
        return computeTemplates;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getRawCloudifyTemplates() throws RestClientException {
        String listTemplatesInternalUrl = getFormattedUrl(this.versionedTemplatesControllerUrl, "", new String[0]);
        Map<String, Object> response = this.executor.get(listTemplatesInternalUrl, new TypeReference<Response<Map<String, Object>>>() {
        });
        return (Map<String, Object>) response.get("templates");
    }

    /**
     * Get the operations outputs of a specific instance of a service
     *
     * @param appName
     * @param serviceName
     * @param instanceId
     * @return
     * @throws RestClientException
     * @throws URISyntaxException
     * @throws IOException
     */
    public Map<String, String> getOperationOutputs(final String appName, final String serviceName, final String instanceId) throws RestClientException,
            URISyntaxException, IOException {
        if (StringUtils.isBlank(instanceId)) {
            log.warn("getOperationOutputs -- InstanceId must not be null or empty");
            return Maps.newIdentityHashMap();
        }

        String attributeUrl = getFormattedUrl(this.versionedDeploymentControllerUrl, GET_SERVICE_INSTANCE_ATTRIBUTES_BIS_URL_FORMAT, appName, serviceName,
                instanceId);
        Map<String, Map<String, Object>> response = this.executor.get(attributeUrl, new TypeReference<Response<Map<String, Map<String, Object>>>>() {
        });
        Map<String, String> outputs = Maps.newHashMap();
        Map<String, Object> attributes = response.get("attributes");
        for (String key : attributes.keySet()) {
            if (key.equals(AlienExtentedConstants.CLOUDIFY_OUTPUTS_ATTRIBUTE)) {
                // For some reason, the value returned by the API is a map.toString(). So impossible to deserialize it using the Json utils
                String outputAsString = MapUtils.getString(attributes, key);
                outputAsString = outputAsString.replaceFirst("\\{", "").replaceAll("}", "");
                if (StringUtils.isNotBlank(outputAsString)) {
                    outputs = Splitter.on(", ").withKeyValueSeparator("=").split(outputAsString);
                }
            }
        }
        return outputs;
    }

    /**
     * - no duplication of templates (AZ not included)
     *
     * @param value
     * @param alreadyAdded
     * @return
     */
    @SuppressWarnings("unchecked")
    private boolean isTemplateEligible(Object value, List<Object> alreadyAdded) {
        Map<String, Object> templateAsMap = Maps.newHashMap((Map<String, Object>) value);

        // remove availability zones before comparing
        templateAsMap.remove("locationId");
        Map<String, Object> custom = (Map<String, Object>) MapUtil.get(templateAsMap, "custom");
        if (custom != null) {
            ((Map<String, Object>) templateAsMap.get("custom")).remove("openstack.compute.zone");
        }
        templateAsMap.remove("availabilityZones");

        // compare with those already added
        if (alreadyAdded.contains(templateAsMap)) {
            return false;
        }

        alreadyAdded.add(templateAsMap);
        return true;
    }

    public void testLogin() throws RestClientException {
        String url = "/service/testlogin";
        executor.get(url, new TypeReference<Response<Object>>() {
        });
    }

    public void test(boolean testLogin) throws RestClientException {
        this.connect();
        if (testLogin) {
            this.testLogin();
        }
    }
}
