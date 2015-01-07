package alien4cloud.paas.cloudify2.generator;

import static alien4cloud.paas.cloudify2.generator.RecipeGeneratorConstants.SCRIPTS;
import static alien4cloud.paas.cloudify2.generator.RecipeGeneratorConstants.SCRIPT_LIFECYCLE;
import static alien4cloud.tosca.normative.ToscaFunctionConstants.HOST;
import static alien4cloud.tosca.normative.ToscaFunctionConstants.PARENT;
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

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;

import alien4cloud.model.components.IOperationParameter;
import alien4cloud.model.components.ImplementationArtifact;
import alien4cloud.model.components.IndexedToscaElement;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.paas.IPaaSTemplate;
import alien4cloud.paas.cloudify2.CloudifyPaaSUtils;
import alien4cloud.paas.cloudify2.VelocityUtil;
import alien4cloud.paas.cloudify2.funtion.FunctionProcessor;
import alien4cloud.paas.model.PaaSNodeTemplate;
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

    protected String getOperationCommandFromInterface(final RecipeGeneratorServiceContext context, final PaaSNodeTemplate nodeTemplate,
            final String interfaceName, final String operationName, final Map<String, String> paramsAsVar, Map<String, String> stringParams) throws IOException {
        String command = null;
        Interface interfaz = nodeTemplate.getIndexedNodeType().getInterfaces().get(interfaceName);
        if (interfaz != null) {
            Operation operation = interfaz.getOperations().get(operationName);
            if (operation != null) {
                command = prepareAndGetCommand(context, nodeTemplate, interfaceName, operationName, paramsAsVar, stringParams, operation);
            }
        }

        return command;
    }

    protected String prepareAndGetCommand(final RecipeGeneratorServiceContext context, final PaaSNodeTemplate nodeTemplate, final String interfaceName,
            final String operationName, final Map<String, String> paramsAsVar, Map<String, String> stringParams, Operation operation) throws IOException {
        String command;
        String relativePath = CloudifyPaaSUtils.getNodeTypeRelativePath(nodeTemplate.getIndexedNodeType());
        stringParams = stringParams == null ? Maps.<String, String> newHashMap() : stringParams;
        addNodeBaseEnvVars(nodeTemplate, stringParams, SELF, PARENT, HOST);
        command = getCommandFromOperation(context, nodeTemplate, interfaceName, operationName, relativePath, operation.getImplementationArtifact(),
                paramsAsVar, stringParams, operation.getInputParameters());
        if (StringUtils.isNotBlank(command)) {
            this.artifactCopier.copyImplementationArtifact(context, nodeTemplate.getCsarPath(), relativePath, operation.getImplementationArtifact());
        }
        return command;
    }

    protected String getCommandFromOperation(final RecipeGeneratorServiceContext context, final IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate,
            final String interfaceName, final String operationName, final String relativePath, final ImplementationArtifact artifact,
            final Map<String, String> varEnvVars, final Map<String, String> stringEnvVars, Map<String, IOperationParameter> inputParameters) throws IOException {
        if (artifact == null || StringUtils.isBlank(artifact.getArtifactRef())) {
            return null;
        }

        Map<String, String> runtimeEvalResults = Maps.newHashMap();
        Map<String, String> stringEvalResults = Maps.newHashMap();
        funtionProcessor.processParameters(inputParameters, stringEvalResults, runtimeEvalResults, basePaaSTemplate, context.getTopologyNodeTemplates());
        stringEvalResults = alien4cloud.utils.CollectionUtils.merge(stringEnvVars, stringEvalResults, false);
        runtimeEvalResults = alien4cloud.utils.CollectionUtils.merge(varEnvVars, runtimeEvalResults, false);

        String scriptPath = relativePath + "/" + artifact.getArtifactRef();
        String operationFQN = basePaaSTemplate.getId() + "." + interfaceName + "." + operationName;
        return commandGenerator.getCommandBasedOnArtifactType(operationFQN, artifact, runtimeEvalResults, stringEvalResults, scriptPath);
    }

    protected void generateScriptWorkflow(final Path servicePath, final Path velocityDescriptorPath, final String lifecycle, final List<String> executions,
            final Map<String, ? extends Object> additionalPropeties) throws IOException {
        Path outputPath = servicePath.resolve(lifecycle + ".groovy");

        Map<String, Object> properties = Maps.newHashMap();
        properties.put(SCRIPT_LIFECYCLE, lifecycle);
        properties.put(SCRIPTS, executions);
        properties = CollectionUtils.merge(additionalPropeties, properties, false);
        VelocityUtil.writeToOutputFile(velocityDescriptorPath, outputPath, properties);
    }

    protected void addNodeBaseEnvVars(final PaaSNodeTemplate nodeTemplate, final Map<String, String> envMap, final String... envKeys) {
        if (envKeys == null) {
            return;
        }
        for (String envKey : envKeys) {
            switch (envKey) {
                case SELF:
                    envMap.put(envKey, nodeTemplate.getId());
                    break;
                case HOST:
                    envMap.put(envKey, CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(nodeTemplate));
                    break;
                case PARENT:
                    envMap.put(envKey, nodeTemplate.getParent() == null ? null : nodeTemplate.getParent().getId());
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
