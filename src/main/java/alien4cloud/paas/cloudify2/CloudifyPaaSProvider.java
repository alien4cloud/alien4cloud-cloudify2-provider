package alien4cloud.paas.cloudify2;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.paas.IManualResourceMatcherPaaSProvider;
import alien4cloud.paas.exception.PluginConfigurationException;

@Component("cloudify-paas-provider-bean")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class CloudifyPaaSProvider extends AbstractCloudifyPaaSProvider<PluginConfigurationBean> implements IManualResourceMatcherPaaSProvider {

    private PluginConfigurationBean configurationBean = new PluginConfigurationBean();

    @Override
    protected PluginConfigurationBean getPluginConfigurationBean() {
        return configurationBean;
    }

    @Override
    public PluginConfigurationBean getDefaultConfiguration() {
        return configurationBean;
    }

    @Override
    public void setConfiguration(PluginConfigurationBean configuration) throws PluginConfigurationException {
        this.configurationBean = configuration;
        this.cloudifyRestClientManager.setCloudifyConnectionConfiguration(configuration.getCloudifyConnectionConfiguration());
        configureDefault();
    }

    @Override
    public void updateMatcherConfig(CloudResourceMatcherConfig cloudResourceMatcherConfig) {
        this.recipeGenerator.getComputeTemplateMatcher().configure(cloudResourceMatcherConfig);
    }
}