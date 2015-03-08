package alien4cloud.paas.cloudify2;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.cloudifysource.restclient.exceptions.RestClientException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import alien4cloud.paas.exception.PluginConfigurationException;

import com.google.common.collect.Lists;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@Slf4j
public class CloudifyRestClientManager {

    private static final Integer REPEAT_INTERVAL_SECONDS = 5;

    @Getter
    private URL cloudifyURL;
    private String username;
    private String password;
    private Integer timeout = 60;

    private String version = "2.7.1";

    private CloudifyRestClient restClient;
    private URI restEventEndpoint;

    private LinkedList<CloudifyConnectionConfiguration> connectionConfigs = new LinkedList<>();

    /**
     * Get the cloudify rest client.
     *
     * @return An instance of the cloudify rest client.
     * @throws PluginConfigurationException In case of an error while creating the cloudify rest client.
     */
    public CloudifyRestClient getRestClient() throws PluginConfigurationException {
        if (restClient == null || !test()) {
            tryCloudifyConfigurations();
        }
        return restClient;
    }

    /**
     * Set the configuration of the cloudify connection.
     *
     * @param cloudifyConnectionConfigurations A list of configuration elements of the cloudify configuration.
     * @param timeout TODO
     * @throws PluginConfigurationException In case the connection configuration is not correct.
     */
    public void setCloudifyConnectionConfiguration(List<CloudifyConnectionConfiguration> cloudifyConnectionConfigurations, Integer timeout)
            throws PluginConfigurationException {
        this.connectionConfigs = Lists.newLinkedList(cloudifyConnectionConfigurations);
        this.timeout = timeout != null ? Math.abs(timeout) : this.timeout;
        tryCloudifyConfigurations();
    }

    private boolean setCloudifyConnectionConfiguration(CloudifyConnectionConfiguration cloudifyConnectionConfiguration) throws PluginConfigurationException {
        log.info("Trying to set Cloudify manager REST API url to <" + cloudifyConnectionConfiguration.getCloudifyURL() + ">");
        this.restClient = null;
        this.restEventEndpoint = null;
        try {
            this.cloudifyURL = new URL(cloudifyConnectionConfiguration.getCloudifyURL());
            this.username = cloudifyConnectionConfiguration.getUsername();
            this.password = cloudifyConnectionConfiguration.getPassword();
            this.version = cloudifyConnectionConfiguration.getVersion();
            this.restClient = new CloudifyRestClient(cloudifyURL, username, password, version);
            this.restClient.connect();
            // check that the events module can be reached too.
            this.restEventEndpoint = new URI(String.format("http://%s:8081", cloudifyURL.getHost()));
            CloudifyEventsListener cloudifyEventsListener = new CloudifyEventsListener(this.restEventEndpoint);
            // check connection
            log.info("Testing events module endpoint " + this.restEventEndpoint + "... ");
            log.info("==> " + cloudifyEventsListener.test());
            log.info("Cloudify rest client manager configuration done.");
            return true;
        } catch (RestClientException | URISyntaxException | IOException e) {
            String cause = e instanceof RestClientException ? ((RestClientException) e).getMessageFormattedText() : e.getMessage();
            log.warn("Failed to set cloudify connexion to " + cloudifyConnectionConfiguration + ".\n\tCause: " + cause);
            log.debug("", e);
            return false;
        }
    }

    /**
     *
     * try configurations for cloudify connexion.
     * Repeat the try a certain number of time before failing.
     * Put the first valid configuration ontop of the list.
     *
     * @param configurations
     * @throws PluginConfigurationException
     * @throws InterruptedException
     */
    private synchronized void tryCloudifyConfigurations() throws PluginConfigurationException {
        Integer repeat_count = Math.round(this.timeout / REPEAT_INTERVAL_SECONDS) - 1;
        do {
            // LinkedList<CloudifyConnectionConfiguration> copyList = new LinkedList<>(connectionConfigs);
            for (int i = 0; i < connectionConfigs.size(); i++) {
                CloudifyConnectionConfiguration config = connectionConfigs.get(i);
                if (setCloudifyConnectionConfiguration(config)) {
                    if (i > 0) {
                        connectionConfigs.remove(i);
                        connectionConfigs.push(config);
                    }
                    return;
                }
            }

            log.warn("None of the provided cloudify connexion configurations is responding. Retry Count left: " + repeat_count + ". Will retry in "
                    + REPEAT_INTERVAL_SECONDS + " seconds...");
            try {
                Thread.sleep(REPEAT_INTERVAL_SECONDS * 1000L);
            } catch (InterruptedException e) {
                log.warn("Interrupted sleep");
                break;
            }
        } while (repeat_count-- > 0);

        throw new PluginConfigurationException("Failed to configure cloudify plugin. None of the provided cloudify connexion configurations is responding.");
    }

    /**
     * Get the endpoint of the cloudify events extension API.
     *
     * @return The URI endpoint for the cloudify events extension.
     */
    public URI getRestEventEndpoint() {
        if (!test()) {
            try {
                tryCloudifyConfigurations();
            } catch (PluginConfigurationException e) {
                return null;
            }
        }
        return restEventEndpoint;
    }

    public boolean test() {
        try {
            restClient.connect();
            CloudifyEventsListener cloudifyEventsListener = new CloudifyEventsListener(this.restEventEndpoint);
            cloudifyEventsListener.test();
            return true;
        } catch (RestClientException | URISyntaxException | IOException e) {
            String cause = e instanceof RestClientException ? ((RestClientException) e).getMessageFormattedText() : e.getMessage();
            log.warn("Fail to connect to cloudify manager rest endpoint: " + cause);
            log.debug("", e);
            return false;
        }
    }
}