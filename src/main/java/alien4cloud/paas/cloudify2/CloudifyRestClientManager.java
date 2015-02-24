package alien4cloud.paas.cloudify2;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Iterator;
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
    @Getter
    private URL cloudifyURL;
    private String username;
    private String password;

    private String version = "2.7.1";

    private CloudifyRestClient restClient;
    private URI restEventEndpoint;

    private LinkedList<CloudifyConnectionConfiguration> connexionConfigs = new LinkedList<>();

    /**
     * Get the cloudify rest client.
     *
     * @return An instance of the cloudify rest client.
     * @throws PluginConfigurationException In case of an error while creating the cloudify rest client.
     */
    public CloudifyRestClient getRestClient() throws PluginConfigurationException {
        if (restClient == null || !restClient.test()) {
            tryCloudifyConfigurations();
        }
        return restClient;
    }

    /**
     * Set the configuration of the cloudify connection.
     *
     * @param cloudifyConnectionConfigurations A list of configuration elements of the cloudify configuration.
     * @throws PluginConfigurationException In case the connection configuration is not correct.
     */
    public void setCloudifyConnectionConfiguration(List<CloudifyConnectionConfiguration> cloudifyConnectionConfigurations) throws PluginConfigurationException {
        this.connexionConfigs = Lists.newLinkedList(cloudifyConnectionConfigurations);
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
            // check that the events module can be reached too.
            this.restEventEndpoint = new URI(String.format("http://%s:8081", cloudifyURL.getHost()));
            CloudifyEventsListener cloudifyEventsListener = new CloudifyEventsListener(this.restEventEndpoint);
            // check connection
            log.info("Testing events module endpoint " + this.restEventEndpoint + "... ==> " + cloudifyEventsListener.test());
            log.info("Cloudify rest client manager configuration done.");
            return true;
        } catch (RestClientException | URISyntaxException | IOException e) {
            String cause = e instanceof RestClientException ? ((RestClientException) e).getMessageFormattedText() : e.getMessage();
            log.warn("Failed to set cloudify connexion to " + cloudifyConnectionConfiguration + ".\n\tCause: " + cause, e);
            return false;
        }
    }

    /**
     *
     * try configurations for cloudify connexion. Put the first valid one ontop of the list.
     *
     * @param configurations
     * @throws PluginConfigurationException
     */
    private void tryCloudifyConfigurations() throws PluginConfigurationException {
        Iterator<CloudifyConnectionConfiguration> iterator = connexionConfigs.iterator();
        while (iterator.hasNext()) {
            CloudifyConnectionConfiguration config = iterator.next();
            if (setCloudifyConnectionConfiguration(config)) {
                if (connexionConfigs.indexOf(config) > 0) {
                    connexionConfigs.remove(config);
                    connexionConfigs.push(config);
                }
                return;
            }
        }
        throw new PluginConfigurationException("Failed to configure cloudify plugin. None of the provided cloudify connexion configurations is responding.");
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