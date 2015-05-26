package alien4cloud.paas.cloudify2.rest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.cloudifysource.dsl.rest.response.Response;
import org.cloudifysource.dsl.rest.response.ServiceInstanceDetails;
import org.cloudifysource.restclient.RestClient;
import org.cloudifysource.restclient.exceptions.RestClientException;
import org.codehaus.jackson.type.TypeReference;

import alien4cloud.paas.cloudify2.AlienExtentedConstants;
import alien4cloud.paas.cloudify2.CloudifyComputeTemplate;
import alien4cloud.paas.cloudify2.GeneratedCloudifyComputeTemplate;
import alien4cloud.paas.cloudify2.utils.CloudifyPaaSUtils;
import alien4cloud.rest.utils.JsonUtil;
import alien4cloud.utils.MapUtil;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Slf4j
public class CloudifyRestClient extends RestClient {

    private static final String GET_SERVICE_INSTANCE_DETAILS_URL_FORMAT = "/%s/service/%s/instances/%s/metadata";
    private static final String GET_SERVICE_ALL_INSTANCES_ATTRIBUTES_URL_FORMAT = "/instances/%s/%s";
    private static final String GET_SERVICE_INSTANCE_ATTRIBUTES_URL_FORMAT = "/instances/%s/%s/%s";
    private static final String GET_SERVICE_INSTANCE_ATTRIBUTE_URL_FORMAT = "/instances/%s/%s/%s/%s";
    private static final String DEPLOYMENT_CONTROLLER_URL = "/deployments";
    private static final String ATTRIBUTES_CONTROLLER_URL = "/attributes";
    private URL url;
    private RestExecutor customExecutor;

    private String versionedDeploymentControllerUrl;

    public CloudifyRestClient(URL url, String username, String password, String apiVersion) throws RestClientException {
        super(url, username, password, apiVersion);
        versionedDeploymentControllerUrl = apiVersion + DEPLOYMENT_CONTROLLER_URL;
        this.url = url;
        this.customExecutor = new RestExecutor();
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
     * Gets multiple attributes' values, from instances in cloudify, scope: instance attributes.
     *
     * @param appName
     *            The application name. Mandatory
     * @param serviceName
     *            The service name; Mandatory
     * @param instanceId
     *            Optional instanceId
     * @param attribute
     *            Optional attribute name
     * @return
     * @throws URISyntaxException
     * @throws IOException
     */
    public Map<String, Object> getCloudifyInstanceAttrbute(final String appName, final String serviceName, final Integer instanceId, final String attribute)
            throws URISyntaxException, IOException {
        // appName and serviceName are mandatories
        if (!StringUtils.isNoneBlank(appName, serviceName)) {
            return null;
        }

        String outputString = null;
        if (instanceId == null) { // Gets multiple attributes' values, from all instances
            outputString = get(ATTRIBUTES_CONTROLLER_URL, GET_SERVICE_ALL_INSTANCES_ATTRIBUTES_URL_FORMAT, appName, serviceName);
        } else if (StringUtils.isBlank(attribute)) { // Gets all attributes' values from an instance
            outputString = get(ATTRIBUTES_CONTROLLER_URL, GET_SERVICE_INSTANCE_ATTRIBUTES_URL_FORMAT, appName, serviceName, String.valueOf(instanceId));
        } else { // Gets a specific attribute' values from an instance
            outputString = get(ATTRIBUTES_CONTROLLER_URL, GET_SERVICE_INSTANCE_ATTRIBUTE_URL_FORMAT, appName, serviceName, String.valueOf(instanceId),
                    attribute);
        }
        return StringUtils.isNotBlank(outputString) ? JsonUtil.toMap(outputString) : null;
    }

    /**
     * Gets multiple attributes' values, from all instances. scope: instance attributes
     *
     * @param appName
     * @param serviceName
     * @return a map off all attributes of all instances:<br>
     *         {"instanceId:attribute1Name":"attribute1Value", "instanceId:attribute2Name":"attribute2Value"}"
     * @throws URISyntaxException
     * @throws IOException
     */
    public Map<String, Object> getAllInstanceAttributes(final String appName, final String serviceName) throws URISyntaxException, IOException {
        return getCloudifyInstanceAttrbute(appName, serviceName, null, null);
    }

    public Map<String, Object> getOperationOutputs(final String appName, final String serviceName, final Integer instanceId) throws RestClientException,
            URISyntaxException, IOException {
        return getCloudifyInstanceAttrbute(appName, serviceName, instanceId, AlienExtentedConstants.CLOUDIFY_OUTPUTS_ATTRIBUTE);
    }

    private String get(String baseUrl, String format, String... args) throws URISyntaxException, IOException {
        if (url == null) {
            return null;
        }
        String formatedUrl = getFormattedUrl(baseUrl, format, args);
        URIBuilder builder = new URIBuilder(url.toURI().resolve(formatedUrl));
        return customExecutor.doGet(builder, false);
    }

    public String getOperationOutput(final String appName, final String serviceName, final Integer instanceId, final String formatedOutputName)
            throws RestClientException, URISyntaxException, IOException {
        Map<String, Object> outputs = getOperationOutputs(appName, serviceName, instanceId);
        return MapUtils.getString(outputs, formatedOutputName);
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
