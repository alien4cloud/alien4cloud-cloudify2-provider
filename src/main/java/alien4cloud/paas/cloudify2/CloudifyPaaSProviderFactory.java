package alien4cloud.paas.cloudify2;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.log4j.Level;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.stereotype.Component;

import alien4cloud.model.components.PropertyConstraint;
import alien4cloud.model.components.PropertyDefinition;
import alien4cloud.model.components.constraints.GreaterThanConstraint;
import alien4cloud.model.components.constraints.ValidValuesConstraint;
import alien4cloud.paas.IConfigurablePaaSProvider;
import alien4cloud.paas.IConfigurablePaaSProviderFactory;
import alien4cloud.paas.IDeploymentParameterizablePaaSProviderFactory;
import alien4cloud.paas.exception.PaaSTechnicalException;
import alien4cloud.tosca.normative.ToscaType;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component("cloudify-paas-provider")
public class CloudifyPaaSProviderFactory implements IConfigurablePaaSProviderFactory<PluginConfigurationBean>,
        IDeploymentParameterizablePaaSProviderFactory<IConfigurablePaaSProvider<PluginConfigurationBean>> {

    private Map<String, PropertyDefinition> deploymentPropertyMap;

    public CloudifyPaaSProviderFactory() {
        setDeploymentProperties();
    }

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
    public Map<String, PropertyDefinition> getDeploymentPropertyDefinitions() {
        return deploymentPropertyMap;
    }

    @Override
    public void destroy(IConfigurablePaaSProvider instance) {
        // Do nothing
    }

    private void setDeploymentProperties() throws PaaSTechnicalException {
        if (deploymentPropertyMap != null) {
            return;
        }
        deploymentPropertyMap = Maps.newHashMap();
        // Field 1 : startDetection_timeout_inSecond as string
        PropertyDefinition startDetectionTimeout = new PropertyDefinition();
        startDetectionTimeout.setType(ToscaType.INTEGER.toString());
        startDetectionTimeout.setRequired(false);
        startDetectionTimeout.setDescription("Cloudify start detection timout in seconds for this deployment.");
        startDetectionTimeout.setDefault("600");
        GreaterThanConstraint detectionConstraint = new GreaterThanConstraint();
        detectionConstraint.setGreaterThan("0");
        startDetectionTimeout.setConstraints(Arrays.asList((PropertyConstraint) detectionConstraint));
        deploymentPropertyMap.put(DeploymentPropertiesNames.STARTDETECTION_TIMEOUT_INSECOND, startDetectionTimeout);

        // Field 2 : disable_self_healing
        PropertyDefinition disableSelfHealing = new PropertyDefinition();
        disableSelfHealing.setType(ToscaType.BOOLEAN.toString());
        disableSelfHealing.setRequired(false);
        disableSelfHealing.setDescription("Whether to disable or not the cloudify's self-healing mechanism for this deployment.");
        disableSelfHealing.setDefault("false");
        deploymentPropertyMap.put(DeploymentPropertiesNames.DISABLE_SELF_HEALING, disableSelfHealing);

        // Field 3 : events_lease_inHour
        PropertyDefinition eventsLease = new PropertyDefinition();
        eventsLease.setType(ToscaType.FLOAT.toString());
        eventsLease.setRequired(false);
        eventsLease.setDescription("Lease time in hour for alien4cloud events.");
        eventsLease.setDefault("2");
        GreaterThanConstraint leaseConstraint = new GreaterThanConstraint();
        leaseConstraint.setGreaterThan("0");
        eventsLease.setConstraints(Arrays.asList((PropertyConstraint) leaseConstraint));
        deploymentPropertyMap.put(DeploymentPropertiesNames.EVENTS_LEASE_INHOUR, eventsLease);

        // Field 4 : deletable_blockstorage (enables
        PropertyDefinition deletableBlockStorage = new PropertyDefinition();
        deletableBlockStorage.setType(ToscaType.BOOLEAN.toString());
        deletableBlockStorage.setRequired(false);
        deletableBlockStorage.setDescription("Indicates that all deployment related blockstorage are deletable.");
        deletableBlockStorage.setDefault("false");
        deploymentPropertyMap.put(DeploymentPropertiesNames.DELETABLE_BLOCKSTORAGE, deletableBlockStorage);

        // Field 5 : log level for this deployment
        PropertyDefinition logLevel = new PropertyDefinition();
        logLevel.setType(ToscaType.STRING.toString());
        logLevel.setRequired(false);
        logLevel.setDescription("Log level for the deployment.");
        logLevel.setDefault(Level.INFO.toString());
        ValidValuesConstraint logLevelConstraint = new ValidValuesConstraint();

        List<String> validLevels = Lists.newArrayList();
        validLevels.add(Level.OFF.toString());
        validLevels.add(Level.INFO.toString());
        validLevels.add(Level.DEBUG.toString());
        validLevels.add(Level.ERROR.toString());
        logLevelConstraint.setValidValues(validLevels);

        logLevel.setConstraints(Arrays.asList((PropertyConstraint) logLevelConstraint));
        deploymentPropertyMap.put(DeploymentPropertiesNames.LOG_LEVEL, logLevel);
    }
}