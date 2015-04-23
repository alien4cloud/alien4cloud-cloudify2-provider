package alien4cloud.paas.cloudify2.rest;

import java.net.URL;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.cloudifysource.dsl.rest.response.Response;
import org.cloudifysource.dsl.rest.response.ServiceInstanceDetails;
import org.cloudifysource.restclient.RestClient;
import org.cloudifysource.restclient.exceptions.RestClientException;
import org.codehaus.jackson.type.TypeReference;

import alien4cloud.paas.cloudify2.CloudifyComputeTemplate;
import alien4cloud.utils.MapUtil;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Slf4j
public class CloudifyRestClient extends RestClient {

    private static final String GET_SERVICE_INSTANCE_DETAILS_URL_FORMAT = "/%s/service/%s/instances/%s/metadata";
    private static final String DEPLOYMENT_CONTROLLER_URL = "/deployments/";

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
    public Map<String, CloudifyComputeTemplate> getCloudifyComputeTemplates() throws RestClientException {
        String listTemplatesInternalUrl = getFormattedUrl(this.versionedTemplatesControllerUrl, "", new String[0]);
        Map<String, Object> response = this.executor.get(listTemplatesInternalUrl, new TypeReference<Response<Map<String, Object>>>() {
        });
        Map<String, CloudifyComputeTemplate> computeTemplates = Maps.newHashMap();
        Map<String, Object> templates = (Map<String, Object>) response.get("templates");
        if (templates == null) {
            return computeTemplates;
        }
        List<Object> alreadyAdded = Lists.newArrayList();
        for (Map.Entry<String, Object> templateEntry : templates.entrySet()) {
            if (isTemplateEligible(templateEntry.getValue(), alreadyAdded)) {
                String imageId = (String) MapUtil.get((Map<String, Object>) templateEntry.getValue(), "imageId");
                String hardwareId = (String) MapUtil.get((Map<String, Object>) templateEntry.getValue(), "hardwareId");
                if (!StringUtils.isEmpty(imageId) && !StringUtils.isEmpty(hardwareId)) {
                    computeTemplates.put(templateEntry.getKey(), new CloudifyComputeTemplate(imageId, hardwareId));
                }
            }
        }
        return computeTemplates;
    }

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
