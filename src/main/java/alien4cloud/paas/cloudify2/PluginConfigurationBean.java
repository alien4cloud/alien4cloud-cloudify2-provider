package alien4cloud.paas.cloudify2;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import alien4cloud.tosca.normative.ToscaType;
import alien4cloud.ui.form.annotation.FormLabel;
import alien4cloud.ui.form.annotation.FormProperties;
import alien4cloud.ui.form.annotation.FormPropertyDefinition;

import com.google.common.collect.Lists;

/**
 * Bean for cloudify configuration.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@FormProperties({ "cloudifyURLs", "username", "password", "version", "connectionTimeOutInSeconds" })
public class PluginConfigurationBean {

    private String version = "2.7.1";
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.CONNECTION_CONFIGURATION.CLOUDIFY_URLS")
    private List<String> cloudifyURLs = Lists.newArrayList("http://localhost:8100");
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.CONNECTION_CONFIGURATION.USER_NAME")
    private String username = "";
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.CONNECTION_CONFIGURATION.PASSWORD")
    @FormPropertyDefinition(type = ToscaType.STRING, isPassword = true)
    private String password = "";
    /** timout to try to connect to the provided urls */
    @FormLabel("CLOUDS.DRIVER.CLOUDIFY.CONNECTION_CONFIGURATION.TIMEOUT")
    private Integer connectionTimeOutInSeconds = 60;
}