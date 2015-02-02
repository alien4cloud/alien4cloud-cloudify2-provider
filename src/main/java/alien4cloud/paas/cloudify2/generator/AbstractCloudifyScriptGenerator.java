package alien4cloud.paas.cloudify2.generator;

import static alien4cloud.paas.cloudify2.generator.AlienEnvironmentVariables.*;
import static alien4cloud.paas.cloudify2.generator.RecipeGeneratorConstants.SCRIPTS;
import static alien4cloud.paas.cloudify2.generator.RecipeGeneratorConstants.SCRIPT_LIFECYCLE;
import static alien4cloud.tosca.normative.ToscaFunctionConstants.HOST;
import static alien4cloud.tosca.normative.ToscaFunctionConstants.SELF;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;

import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IOperationParameter;
import alien4cloud.model.components.ImplementationArtifact;
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
    private RecipePropertiesGenerator recipePropertiesGenerator;
    @Resource
    private ApplicationContext applicationContext;

    private static final String MAP_TO_ADD_KEYWORD = "MAP_TO_ADD_";

    protected String getOperationCommandFromInterface(final RecipeGeneratorServiceContext context, final PaaSNodeTemplate nodeTemplate,
            final String interfaceName, final String operationName, final Map<String, String> paramsAsVar, Map<String, String> stringParams) throws IOException {
        String command = null;
        Interface interfaz = nodeTemplate.getIndexedToscaElement().getInterfaces().get(interfaceName);
        if (interfaz != null) {
            Operation operation = interfaz.getOperations().get(operationName);
            if (operation != null) {
                command = prepareAndGetCommand(context, nodeTemplate, interfaceName, operationName, paramsAsVar, stringParams, operation);
            }
        }

        return command;
    }

    protected String prepareAndGetCommand(final RecipeGeneratorServiceContext context, final PaaSNodeTemplate nodeTemplate, final String interfaceName,
            final String operationName, final Map<String, String> paramsAsVar, Map<String, String> stringParams, Operation operation)
            throws IOException {
        String command;
        stringParams = stringParams == null ? Maps.<String, String> newHashMap() : stringParams;
        command = getCommandFromOperation(context, nodeTemplate, interfaceName, operationName, operation.getImplementationArtifact(), paramsAsVar,
                stringParams, operation.getInputParameters());
        if (StringUtils.isNotBlank(command)) {
            this.artifactCopier.copyImplementationArtifact(context, nodeTemplate.getCsarPath(), operation.getImplementationArtifact(),
                    nodeTemplate.getIndexedToscaElement());
        }
        return command;
    }

    protected String getCommandFromOperation(final RecipeGeneratorServiceContext context, final IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate,
            final String interfaceName, final String operationName, final ImplementationArtifact artifact, final Map<String, String> varEnvVars,
            final Map<String, String> stringEnvVars, Map<String, IOperationParameter> inputParameters) throws IOException {
        if (artifact == null || StringUtils.isBlank(artifact.getArtifactRef())) {
            return null;
        }

        Map<String, String> runtimeEvalResults = Maps.newHashMap();
        Map<String, String> stringEvalResults = Maps.newHashMap();
        funtionProcessor.processParameters(inputParameters, stringEvalResults, runtimeEvalResults, basePaaSTemplate, context.getTopologyNodeTemplates());
        if (stringEnvVars != null) {
            stringEvalResults.putAll(stringEnvVars);
        }
        if (varEnvVars != null) {
            runtimeEvalResults.putAll(varEnvVars);
        }
        // if relationship, add relationship env vars
        if (basePaaSTemplate instanceof PaaSRelationshipTemplate) {
            addRelationshipEnvVars(inputParameters, stringEvalResults, runtimeEvalResults, (PaaSRelationshipTemplate) basePaaSTemplate,
                    context.getTopologyNodeTemplates());
        } else {
            addNodeBaseEnvVars((PaaSNodeTemplate) basePaaSTemplate, stringEvalResults, SELF, HOST, SERVICE_NAME);
        }

        String relativePath = CloudifyPaaSUtils.getNodeTypeRelativePath(basePaaSTemplate.getIndexedToscaElement());
        String scriptPath = relativePath + "/" + artifact.getArtifactRef();
        String operationFQN = basePaaSTemplate.getId() + "." + interfaceName + "." + operationName;
        return commandGenerator.getCommandBasedOnArtifactType(operationFQN, artifact, runtimeEvalResults, stringEvalResults, scriptPath);
    }

    private void addRelationshipEnvVars(Map<String, IOperationParameter> inputParameters, Map<String, String> stringEvalResults,
            Map<String, String> runtimeEvalResults, PaaSRelationshipTemplate basePaaSTemplate, Map<String, PaaSNodeTemplate> builtPaaSTemplates)
            throws IOException {

        Map<String, String> targetAttributes = Maps.newHashMap();
        Map<String, String> sourceAttributes = Maps.newHashMap();
        String sourceId = CloudifyPaaSUtils.serviceIdFromNodeTemplateId(basePaaSTemplate.getSource());
        String targetId = CloudifyPaaSUtils.serviceIdFromNodeTemplateId(basePaaSTemplate.getRelationshipTemplate().getTarget());
        String sourceServiceName = CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(builtPaaSTemplates.get(basePaaSTemplate.getSource()));
        String targetServiceName = CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(builtPaaSTemplates.get(basePaaSTemplate.getRelationshipTemplate()
                .getTarget()));

        // custom alien env vars
        stringEvalResults.put(SOURCE_NAME, basePaaSTemplate.getSource());
        stringEvalResults.put(TARGET_NAME, basePaaSTemplate.getRelationshipTemplate().getTarget());
        stringEvalResults.put(SOURCE_SERVICE_NAME, CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(builtPaaSTemplates.get(basePaaSTemplate.getSource())));
        stringEvalResults.put(TARGET_SERVICE_NAME,
                CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(builtPaaSTemplates.get(basePaaSTemplate.getRelationshipTemplate().getTarget())));

        // separate target and source attributes
        if (inputParameters != null) {
            for (Entry<String, IOperationParameter> paramEntry : inputParameters.entrySet()) {
                if (!paramEntry.getValue().isDefinition() && FunctionEvaluator.isGetAttribute((FunctionPropertyValue) paramEntry.getValue())) {
                    FunctionPropertyValue param = (FunctionPropertyValue) paramEntry.getValue();
                    if (ToscaFunctionConstants.TARGET.equals(FunctionEvaluator.getEntityName(param))) {
                        targetAttributes.put(paramEntry.getKey(), FunctionEvaluator.getElementName(param));
                    } else if (ToscaFunctionConstants.SOURCE.equals(FunctionEvaluator.getEntityName(param))) {
                        sourceAttributes.put(paramEntry.getKey(), FunctionEvaluator.getElementName(param));
                    }
                }
            }
        }

        // TOSCA SOURCE/SOURCES and TARGET/TARGETS
        runtimeEvalResults.put(MAP_TO_ADD_KEYWORD + ToscaFunctionConstants.SOURCE,
                commandGenerator.getTOSCARelationshipEnvsCommand(ToscaFunctionConstants.SOURCE, sourceId, sourceServiceName, sourceAttributes));
        runtimeEvalResults.put(MAP_TO_ADD_KEYWORD + ToscaFunctionConstants.TARGET,
                commandGenerator.getTOSCARelationshipEnvsCommand(ToscaFunctionConstants.TARGET, targetId, targetServiceName, targetAttributes));
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

    private void addNodeBaseEnvVars(final PaaSNodeTemplate nodeTemplate, final Map<String, String> envMap, final String... envKeys) {
        if (envKeys == null) {
            return;
        }
        for (String envKey : envKeys) {
            switch (envKey) {
                case SELF:
                    envMap.put(envKey, nodeTemplate.getId());
                    break;
                case HOST:
                    envMap.put(envKey, nodeTemplate.getParent() == null ? null : nodeTemplate.getParent().getId());
                    break;
                case SERVICE_NAME:
                    envMap.put(envKey, CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(nodeTemplate));
                    break;
                default:
                    break;
            }
        }
    }

    protected Path loadResourceFromClasspath(String resource) throws IOException {
        URI uri = applicationContext.getResource(resource).getURI();
        String uriStr = uri.toString();
        Path path = null;
        if (uriStr.contains("!")) {
            FileSystem fs = null;
            try {
                String[] array = uriStr.split("!");
                fs = FileSystems.newFileSystem(URI.create(array[0]), new HashMap<String, Object>());
                path = fs.getPath(array[1]);

                // Hack to avoid classloader issues
                Path createTempFile = Files.createTempFile("velocity", ".vm");
                createTempFile.toFile().deleteOnExit();
                Files.copy(path, createTempFile, StandardCopyOption.REPLACE_EXISTING);

                path = createTempFile;
            } finally {
                if (fs != null) {
                    fs.close();
                }
            }
        } else {
            path = Paths.get(uri);
        }
        return path;
    }

    protected void generatePropertiesFile(RecipeGeneratorServiceContext context, PaaSNodeTemplate serviceRootTemplate) throws IOException {
        Path descriptorPath = loadResourceFromClasspath("classpath:velocity/ServiceProperties.vm");
        recipePropertiesGenerator.generatePropertiesFile(context, serviceRootTemplate, descriptorPath);
    }

}
