package alien4cloud.paas.cloudify2;

import java.net.URL;

import lombok.extern.slf4j.Slf4j;

import org.cloudifysource.dsl.rest.response.Response;
import org.cloudifysource.dsl.rest.response.ServiceInstanceDetails;
import org.cloudifysource.restclient.RestClient;
import org.cloudifysource.restclient.exceptions.RestClientException;
import org.codehaus.jackson.type.TypeReference;

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
}
