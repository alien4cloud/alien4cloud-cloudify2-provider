package alien4cloud.paas.cloudify2;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import alien4cloud.ui.form.annotation.FormLabel;

/**
 * Bean for cloudify configuration.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PluginConfigurationBean {
    /** Url of the cloudify manager. */
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.CLOUDIFY_CONNECTION_CONFIGURATION")
    private CloudifyConnectionConfiguration cloudifyConnectionConfiguration = new CloudifyConnectionConfiguration();
    /** True if the undeployment must be performed synchronously, false if not. */
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.SYNCHRONOUS_DEPLOYMENT")
    private boolean synchronousDeployment = false;
}