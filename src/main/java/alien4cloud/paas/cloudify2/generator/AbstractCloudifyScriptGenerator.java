package alien4cloud.paas.cloudify2.generator;

import static alien4cloud.paas.cloudify2.generator.AlienEnvironmentVariables.SERVICE_NAME;
import static alien4cloud.paas.cloudify2.generator.AlienEnvironmentVariables.SOURCE_NAME;
import static alien4cloud.paas.cloudify2.generator.AlienEnvironmentVariables.SOURCE_SERVICE_NAME;
import static alien4cloud.paas.cloudify2.generator.AlienEnvironmentVariables.TARGET_NAME;
import static alien4cloud.paas.cloudify2.generator.AlienEnvironmentVariables.TARGET_SERVICE_NAME;
import static alien4cloud.paas.cloudify2.generator.RecipeGeneratorConstants.SCRIPTS;
import static alien4cloud.paas.cloudify2.generator.RecipeGeneratorConstants.SCRIPT_LIFECYCLE;
import static alien4cloud.tosca.normative.ToscaFunctionConstants.HOST;
import static alien4cloud.tosca.normative.ToscaFunctionConstants.SELF;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Resource;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;

import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IOperationParameter;
import alien4cloud.model.components.ImplementationArtifact;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import alien4cloud.model.components.IndexedToscaElement;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.paas.IPaaSTemplate;
import alien4cloud.paas.cloudify2.funtion.FunctionProcessor;
import alien4cloud.paas.cloudify2.utils.CloudifyPaaSUtils;
import alien4cloud.paas.cloudify2.utils.VelocityUtil;
import alien4cloud.paas.function.FunctionEvaluator;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.plan.ToscaRelationshipLifecycleConstants;
import alien4cloud.tosca.normative.ToscaFunctionConstants;
import alien4cloud.utils.CollectionUtils;

import com.google.common.collect.Maps;

abstract class AbstractCloudifyScriptGenerator {

    @Resource
    protected FunctionProcessor funtionProcessor;
    @Resource
    protected RecipeGeneratorArtifactCopier artifactCopier;
    @Resource
    protected CommandGenerator commandGenerator;
    @Resource
    ApplicationContext applicationContext;

    private static final String MAP_TO_ADD_KEYWORD = "MAP_TO_ADD_";

    protected String getOperationCommandFromInterface(final RecipeGeneratorServiceContext context, final PaaSNodeTemplate nodeTemplate,
            final String interfaceName, final String operationName, ExecEnvMaps envMaps) throws IOException {
        String command = null;
        Interface interfaz = nodeTemplate.getIndexedToscaElement().getInterfaces().get(interfaceName);
        if (interfaz != null) {
            Operation operation = interfaz.getOperations().get(operationName);
            if (operation != null) {
                command = prepareAndGetCommand(context, nodeTemplate, interfaceName, operationName, envMaps, operation);
            }
        }

        return command;
    }

    protected String prepareAndGetCommand(final RecipeGeneratorServiceContext context, final IPaaSTemplate<? extends IndexedArtifactToscaElement> paaSTemplate,
            final String interfaceName, final String operationName, ExecEnvMaps envMaps, Operation operation) throws IOException {
        String command;
        command = getCommandFromOperation(context, paaSTemplate, interfaceName, operationName, operation.getImplementationArtifact(),
                operation.getInputParameters(), null, envMaps);
        if (StringUtils.isNotBlank(command)) {
            this.artifactCopier.copyImplementationArtifact(context, paaSTemplate.getCsarPath(), operation.getImplementationArtifact(),
                    paaSTemplate.getIndexedToscaElement());
        }
        return command;
    }

    protected String getCommandFromOperation(final RecipeGeneratorServiceContext context, final IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate,
            final String interfaceName, final String operationName, final ImplementationArtifact artifact, Map<String, IOperationParameter> inputParameters,
            String instanceId, ExecEnvMaps envMaps) throws IOException {
        if (artifact == null || StringUtils.isBlank(artifact.getArtifactRef())) {
            return null;
        }

        // if relationship, add relationship env vars
        if (basePaaSTemplate instanceof PaaSRelationshipTemplate) {
            addRelationshipEnvVars(operationName, inputParameters, (PaaSRelationshipTemplate) basePaaSTemplate, context.getTopologyNodeTemplates(), instanceId,
                    envMaps);
        } else {
            addNodeEnvVars(context, (PaaSNodeTemplate) basePaaSTemplate, instanceId, inputParameters, envMaps, SELF, HOST, SERVICE_NAME);
        }

        String relativePath = CloudifyPaaSUtils.getNodeTypeRelativePath(basePaaSTemplate.getIndexedToscaElement());
        String scriptPath = relativePath + "/" + artifact.getArtifactRef();
        String operationFQN = basePaaSTemplate.getId() + "." + interfaceName + "." + operationName;
        return commandGenerator.getCommandBasedOnArtifactType(operationFQN, artifact, envMaps.runtimes, envMaps.strings, scriptPath);
    }

    private void addRelationshipEnvVars(String operationName, Map<String, IOperationParameter> inputParameters, PaaSRelationshipTemplate basePaaSTemplate,
            Map<String, PaaSNodeTemplate> builtPaaSTemplates, String instanceId, ExecEnvMaps envMaps) throws IOException {

        Map<String, String> sourceAttributes = Maps.newHashMap();
        Map<String, String> targetAttributes = Maps.newHashMap();

        // for some cases we need to use a value provided in the velocity template.
        // for example for relationship add_source, the source ip_address and instanceId are var provided in the velocity script.
        // The target ip_address and instanceId will remain unchanged and handled by the default routine
        String sourceInstanceId = getProperValueForRelEnvsBuilding(operationName, ToscaFunctionConstants.SOURCE, instanceId);
        String sourceIpAddrVar = getProperValueForRelEnvsBuilding(operationName, ToscaFunctionConstants.SOURCE, "ip_address");
        String sourceId = CloudifyPaaSUtils.serviceIdFromNodeTemplateId(basePaaSTemplate.getSource());
        String targetInstanceId = getProperValueForRelEnvsBuilding(operationName, ToscaFunctionConstants.TARGET, instanceId);
        String targetIpAddrVar = getProperValueForRelEnvsBuilding(operationName, ToscaFunctionConstants.TARGET, "ip_address");
        String targetId = CloudifyPaaSUtils.serviceIdFromNodeTemplateId(basePaaSTemplate.getRelationshipTemplate().getTarget());
        String sourceServiceName = CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(builtPaaSTemplates.get(basePaaSTemplate.getSource()));
        String targetServiceName = CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(builtPaaSTemplates.get(basePaaSTemplate.getRelationshipTemplate()
                .getTarget()));

        // separate parameters using TARGET and SOURCE keywords before processing them
        if (inputParameters != null) {
            Map<String, IOperationParameter> sourceAttrParams = Maps.newHashMap();
            Map<String, IOperationParameter> targetAttrParams = Maps.newHashMap();
            Map<String, IOperationParameter> simpleParams = Maps.newHashMap();
            for (Entry<String, IOperationParameter> paramEntry : inputParameters.entrySet()) {
                if (!paramEntry.getValue().isDefinition()) {
                    if (FunctionEvaluator.isGetAttribute((FunctionPropertyValue) paramEntry.getValue())) {
                        FunctionPropertyValue param = (FunctionPropertyValue) paramEntry.getValue();
                        if (ToscaFunctionConstants.TARGET.equals(param.getTemplateName())) {
                            targetAttrParams.put(paramEntry.getKey(), param);
                            targetAttributes.put(paramEntry.getKey(), param.getPropertyOrAttributeName());
                        } else if (ToscaFunctionConstants.SOURCE.equals(param.getTemplateName())) {
                            sourceAttrParams.put(paramEntry.getKey(), param);
                            sourceAttributes.put(paramEntry.getKey(), param.getPropertyOrAttributeName());
                        }
                    } else {
                        simpleParams.put(paramEntry.getKey(), paramEntry.getValue());
                    }
                }
            }

            // evaluate params
            funtionProcessor.processParameters(simpleParams, envMaps.strings, envMaps.runtimes, basePaaSTemplate, builtPaaSTemplates, null);
            funtionProcessor.processParameters(sourceAttrParams, envMaps.strings, envMaps.runtimes, basePaaSTemplate, builtPaaSTemplates, sourceInstanceId);
            funtionProcessor.processParameters(targetAttrParams, envMaps.strings, envMaps.runtimes, basePaaSTemplate, builtPaaSTemplates, targetInstanceId);

            // override ip attributes' way of getting if needed
            overrideIpAttributesIfNeeded(sourceAttributes, envMaps.runtimes, sourceIpAddrVar);
            overrideIpAttributesIfNeeded(targetAttributes, envMaps.runtimes, targetIpAddrVar);

        }

        // custom alien env vars
        envMaps.strings.put(SOURCE_NAME, basePaaSTemplate.getSource());
        envMaps.strings.put(TARGET_NAME, basePaaSTemplate.getRelationshipTemplate().getTarget());
        envMaps.strings.put(SOURCE_SERVICE_NAME, CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(builtPaaSTemplates.get(basePaaSTemplate.getSource())));
        envMaps.strings.put(TARGET_SERVICE_NAME,
                CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(builtPaaSTemplates.get(basePaaSTemplate.getRelationshipTemplate().getTarget())));

        // TOSCA SOURCE/SOURCES and TARGET/TARGETS
        envMaps.runtimes.put(MAP_TO_ADD_KEYWORD + ToscaFunctionConstants.SOURCE, commandGenerator.getTOSCARelationshipEnvsCommand(
                ToscaFunctionConstants.SOURCE, sourceId, sourceServiceName, sourceInstanceId, sourceAttributes));

        envMaps.runtimes.put(MAP_TO_ADD_KEYWORD + ToscaFunctionConstants.TARGET, commandGenerator.getTOSCARelationshipEnvsCommand(
                ToscaFunctionConstants.TARGET, targetId, targetServiceName, targetInstanceId, targetAttributes));
    }

    private void overrideIpAttributesIfNeeded(Map<String, String> attributes, Map<String, String> evaluated, String overrideValue) {
        if (overrideValue != null) {
            for (Entry<String, String> attrEntry : attributes.entrySet()) {
                if (attrEntry.getValue().equals(AlienExtentedConstants.IP_ADDRESS) && evaluated.containsKey(attrEntry.getKey())) {
                    evaluated.put(attrEntry.getKey(), overrideValue);
                }
            }
        }
    }

    private String getProperValueForRelEnvsBuilding(String operationName, String member, String defaultValue) {
        switch (operationName) {
        case ToscaRelationshipLifecycleConstants.ADD_TARGET:
        case ToscaRelationshipLifecycleConstants.REMOVE_TARGET:
            return member.equals(ToscaFunctionConstants.SOURCE) ? null : defaultValue;
        case ToscaRelationshipLifecycleConstants.ADD_SOURCE:
        case ToscaRelationshipLifecycleConstants.REMOVE_SOURCE:
            return member.equals(ToscaFunctionConstants.TARGET) ? null : defaultValue;
        default:
            return null;
        }
    }

    protected void generateScriptWorkflow(final Path servicePath, final Path velocityDescriptorPath, final String lifecycle, final List<String> executions,
            final Map<String, ? extends Object> additionalPropeties) throws IOException {
        Path outputPath = servicePath.resolve(lifecycle + ".groovy");

        Map<String, Object> properties = Maps.newHashMap();
        properties.put(SCRIPT_LIFECYCLE, lifecycle);
        properties.put(SCRIPTS, executions);
        properties = CollectionUtils.merge(additionalPropeties, properties, true);
        VelocityUtil.writeToOutputFile(velocityDescriptorPath, outputPath, properties);
    }

    private void addNodeEnvVars(final RecipeGeneratorServiceContext context, final PaaSNodeTemplate nodeTemplate, final String instanceId,
            Map<String, IOperationParameter> inputParameters, ExecEnvMaps envMaps, String... envKeys) {
        funtionProcessor.processParameters(inputParameters, envMaps.strings, envMaps.runtimes, nodeTemplate, context.getTopologyNodeTemplates(), instanceId);
        if (envKeys != null) {
            for (String envKey : envKeys) {
                switch (envKey) {
                case SELF:
                    envMaps.strings.put(envKey, nodeTemplate.getId());
                    break;
                case HOST:
                    envMaps.strings.put(envKey, nodeTemplate.getParent() == null ? null : nodeTemplate.getParent().getId());
                    break;
                case SERVICE_NAME:
                    envMaps.strings.put(envKey, CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(nodeTemplate));
                    break;
                default:
                    break;
                }
            }
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    protected class ExecEnvMaps {
        Map<String, String> strings = Maps.newHashMap();
        Map<String, String> runtimes = Maps.newHashMap();
    }

}
