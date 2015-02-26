package alien4cloud.paas.cloudify2;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import alien4cloud.ui.form.annotation.FormLabel;

import com.google.common.collect.Lists;

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
    private List<CloudifyConnectionConfiguration> cloudifyConnectionConfigurations = Lists.newLinkedList(Lists
            .newArrayList(new CloudifyConnectionConfiguration()));
    /** timout to try to connect to the provided urls */
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.CONNECTION_CONFIGURATION.TIMEOUT")
    private Integer connectionTimeOutInSeconds = 60;
    /** True if the undeployment must be performed synchronously, false if not. */
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.SYNCHRONOUS_DEPLOYMENT")
    private boolean synchronousDeployment = false;
}