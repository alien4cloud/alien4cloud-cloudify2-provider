package alien4cloud.paas.cloudify2;

import javax.annotation.Resource;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import alien4cloud.paas.IPaaSProvider;
import alien4cloud.paas.IPaaSProviderFactory;

@Component("cloudify-paas-provider")
public class CloudifyPaaSProviderFactory implements IPaaSProviderFactory {
    @Resource
    private BeanFactory beanFactory;

    @Override
    public IPaaSProvider newInstance() {
        return beanFactory.getBean(CloudifyPaaSProvider.class);
    }
}