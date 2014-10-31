package alien4cloud.paas.cloudify2;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.cloudifysource.restclient.exceptions.RestClientException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import alien4cloud.paas.exception.PluginConfigurationException;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Slf4j
public class CloudifyRestClientManager {
    @Getter
    private URL cloudifyURL;
    private String username;
    private String password;

    private String version = "2.7.1";

    private CloudifyRestClient restClient;
    private URI restEventEndpoint;

    /**
     * Get the cloudify rest client.
     *
     * @return An instance of the cloudify rest client.
     * @throws RestClientException In case of an error while creating the cloudify rest client.
     */
    public CloudifyRestClient getRestClient() throws RestClientException {
        if (restClient == null && cloudifyURL != null) {
            this.restClient = new CloudifyRestClient(cloudifyURL, username, password, version);
        }
        return restClient;
    }

    /**
     * Set the configuration of the cloudify connection.
     *
     * @param cloudifyConnectionConfiguration The configuration elements of the cloudify configuration.
     * @throws PluginConfigurationException In case the connection configuration is not correct.
     */
    public void setCloudifyConnectionConfiguration(CloudifyConnectionConfiguration cloudifyConnectionConfiguration) throws PluginConfigurationException {
        log.info("Cloudify manager REST API url is set to <" + cloudifyConnectionConfiguration.getCloudifyURL() + ">");
        this.restClient = null;
        this.restEventEndpoint = null;
        try {
            this.cloudifyURL = new URL(cloudifyConnectionConfiguration.getCloudifyURL());
            this.username = cloudifyConnectionConfiguration.getUsername();
            this.password = cloudifyConnectionConfiguration.getPassword();
            this.version = cloudifyConnectionConfiguration.getVersion();
            this.restClient = new CloudifyRestClient(cloudifyURL, username, password, version);
            // check that the events module can be reached too.
            this.restEventEndpoint = new URI(String.format("http://%s:8081", cloudifyURL.getHost()));
            CloudifyEventsListener cloudifyEventsListener = new CloudifyEventsListener(this.restEventEndpoint);
            // check connection
            cloudifyEventsListener.test();
        } catch (RestClientException | URISyntaxException | IOException e) {
            log.error("Failed to configure cloudify plugin.", e);
            throw new PluginConfigurationException("Failed to configure cloudify plugin.", e);
        }
    }

    /**
     * Get the endpoint of the cloudify events extension API.
     *
     * @return The URI endpoint for the cloudify events extension.
     */
    public URI getRestEventEndpoint() {
        return restEventEndpoint;
    }
}