package alien4cloud.paas.cloudify2;

import javax.annotation.Resource;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import alien4cloud.paas.IConfigurablePaaSProvider;
import alien4cloud.paas.IConfigurablePaaSProviderFactory;

@Component("cloudify-paas-provider")
public class CloudifyPaaSProviderFactory implements IConfigurablePaaSProviderFactory<PluginConfigurationBean> {
    @Resource
    private BeanFactory beanFactory;

    @Override
    public Class<PluginConfigurationBean> getConfigurationType() {
        return PluginConfigurationBean.class;
    }

    @Override
    public PluginConfigurationBean getDefaultConfiguration() {
        return new PluginConfigurationBean();
    }

    @Override
    public IConfigurablePaaSProvider newInstance() {
        return beanFactory.getBean(CloudifyPaaSProvider.class);
    }

    @Override
    public void destroy(IConfigurablePaaSProvider instance) {
        // Do nothing
    }
}