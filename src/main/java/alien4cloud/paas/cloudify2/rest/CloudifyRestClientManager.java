package alien4cloud.paas.cloudify2.rest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.LinkedList;

import javax.annotation.Resource;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.cloudifysource.restclient.exceptions.RestClientException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import alien4cloud.paas.cloudify2.PluginConfigurationBean;
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

    private LinkedList<String> providedURLs = new LinkedList<>();

    @Resource(name = "cloudify-rest-executor-bean")
    private RestExecutor restExecutor;

    /**
     * Get the cloudify rest client.
     *
     * @return An instance of the cloudify rest client.
     * @throws PluginConfigurationException In case of an error while creating the cloudify rest client.
     */
    public CloudifyRestClient getRestClient() throws PluginConfigurationException {
        if (!test()) {
            tryCloudifyURLs();
        }
        return restClient;
    }

    /**
     * Set the configuration of the cloudify connection.
     *
     * @param configuration A list of configuration elements of the cloudify configuration.
     * @throws PluginConfigurationException In case the connection configuration is not correct.
     */
    public void setCloudifyConnectionConfiguration(PluginConfigurationBean configuration) throws PluginConfigurationException {
        this.providedURLs = Lists.newLinkedList(configuration.getCloudifyURLs());
        this.username = configuration.getUsername();
        this.password = configuration.getPassword();
        this.version = configuration.getVersion();
        this.timeout = configuration.getConnectionTimeOutInSeconds() != null ? Math.abs(configuration.getConnectionTimeOutInSeconds()) : this.timeout;
        tryCloudifyURLs();
    }

    private boolean tryCloudifyURL(String cloudifyURLString) throws PluginConfigurationException {
        log.info("Trying to set Cloudify manager REST API url to <" + cloudifyURLString + ">");
        this.restClient = null;
        this.restEventEndpoint = null;
        try {
            this.cloudifyURL = new URL(cloudifyURLString);
            this.restClient = new CloudifyRestClient(cloudifyURL, username, password, version);
            this.restClient.test(StringUtils.isNoneBlank(this.username, this.password));
            // check that the events module can be reached too.
            setEventRestEndPoint();
            CloudifyEventsListener cloudifyEventsListener = new CloudifyEventsListener(this.restEventEndpoint, this.restExecutor);
            // check connection
            log.info("Testing events module endpoint " + this.restEventEndpoint + "... ");
            log.info("Events module endpoint response: " + cloudifyEventsListener.test());
            log.info("Cloudify rest client manager configuration done.");
            return true;
        } catch (RestClientException | URISyntaxException | IOException e) {
            String cause = e instanceof RestClientException ? ((RestClientException) e).getMessageFormattedText() : e.getMessage();
            log.warn("Failed to set Cloudify manager REST API url to " + cloudifyURLString + ".\n\tCause: " + cause);
            log.debug("", e);
            return false;
        }
    }

    private synchronized void setEventRestEndPoint() throws URISyntaxException {
        this.restEventEndpoint = new URI(String.format("http://%s:8081", cloudifyURL.getHost()));
    }

    /**
     *
     * try cloudify urls connexion.
     * Repeat the try a certain number of time before failing.
     * Put the first valid configuration ontop of the list.
     *
     * @throws PluginConfigurationException
     * @throws InterruptedException
     */
    private synchronized void tryCloudifyURLs() throws PluginConfigurationException {
        Integer repeat_count = Math.round(this.timeout / REPEAT_INTERVAL_SECONDS) - 1;
        do {
            // LinkedList<CloudifyConnectionConfiguration> copyList = new LinkedList<>(connectionConfigs);
            for (int i = 0; i < providedURLs.size(); i++) {
                String cloudifyUrlToTry = providedURLs.get(i);
                if (tryCloudifyURL(cloudifyUrlToTry)) {
                    if (i > 0) {
                        providedURLs.remove(i);
                        providedURLs.push(cloudifyUrlToTry);
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
                tryCloudifyURLs();
            } catch (PluginConfigurationException e) {
                return null;
            }
        }
        return restEventEndpoint;
    }

    public boolean test() {
        if (restClient == null) {
            return false;
        }

        try {
            restClient.test(StringUtils.isNoneBlank(this.username, this.password));
            if (restEventEndpoint == null) {
                setEventRestEndPoint();
            }
            CloudifyEventsListener cloudifyEventsListener = new CloudifyEventsListener(this.restEventEndpoint, this.restExecutor);
            cloudifyEventsListener.test();
            return true;
        } catch (Exception e) {
            String cause = e instanceof RestClientException ? ((RestClientException) e).getMessageFormattedText() : e.getMessage();
            log.warn("Fail to connect to cloudify manager rest endpoint: " + cause);
            log.debug("", e);
            return false;
        }
    }

    public RestExecutor getRestExecutor() {
        return restExecutor;
    }

    public void destroy() {
        try {
            this.restExecutor.shutDown();
        } catch (Exception e) {
            log.error("Could not destroy the Alien rest client", e);
        }
        try {
            this.restClient.shutDown();
        } catch (Exception e) {
            log.error("Could not destroy the Cloudify rest client", e);
        }
    }
}