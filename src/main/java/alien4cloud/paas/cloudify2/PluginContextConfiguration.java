package alien4cloud.paas.cloudify2;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

@Configuration
@ComponentScan("alien4cloud.paas.cloudify2")
@ImportResource("classpath:properties-config.xml")
public class PluginContextConfiguration {
}
