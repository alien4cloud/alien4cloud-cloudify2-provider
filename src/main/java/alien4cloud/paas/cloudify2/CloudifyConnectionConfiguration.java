package alien4cloud.paas.cloudify2;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import alien4cloud.ui.form.annotation.FormLabel;

/**
 * Configuration for the cloudify connection.
 */
@Getter
@Setter
@ToString
public class CloudifyConnectionConfiguration {
    private String version = "2.7.1";
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.CONNECTION_CONFIGURATION.CLOUDIFY_URL")
    private String cloudifyURL = "http://localhost:8100";
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.CONNECTION_CONFIGURATION.USER_NAME")
    private String username = "";
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.CONNECTION_CONFIGURATION.PASSWORD")
    private String password = "";
}