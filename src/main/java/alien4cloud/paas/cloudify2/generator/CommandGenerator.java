package alien4cloud.paas.cloudify2.generator;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import alien4cloud.model.components.ImplementationArtifact;
import alien4cloud.paas.cloudify2.AlienExtentedConstants;
import alien4cloud.paas.cloudify2.DeploymentPropertiesNames;
import alien4cloud.paas.cloudify2.ProviderLogLevel;
import alien4cloud.paas.cloudify2.ServiceSetup;
import alien4cloud.paas.cloudify2.utils.VelocityUtil;
import alien4cloud.paas.exception.PaaSDeploymentException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;

/**
 * Generate properly formated commands for cloudify recipes.
 */
@Component
@Slf4j
public class CommandGenerator {
    private final static String[] SERVICE_RECIPE_RESOURCES = new String[] { "chmod-init.groovy", "CloudifyUtils.groovy", "CloudifyExecutorUtils.groovy",
            "CloudifyAttributesUtils.groovy", "EnvironmentBuilder.groovy", "scriptWrapper/scriptWrapper.sh", "scriptWrapper/scriptWrapper.bat",
            "ProcessOutputListener.groovy" };
    private final static String[] SERVICE_RECIPE_RESOURCES_VELOCITY_TEMP = new String[] { "GigaSpacesEventsManager", "DeploymentConstants" };

    private final static String USM_LIB_DIR = "src/main/resources/usmlib";
    private final static String RECIPE_USM_LIB_DIR = "usmlib";

    private final static String OPERATION_FQN = "OPERATION_FQN";

    private static final String FIRE_EVENT_FORMAT = "CloudifyExecutorUtils.fireEvent(\"%s\", \"%s\", %s)";
    private static final String FIRE_BLOCKSTORAGE_EVENT_FORMAT = "CloudifyExecutorUtils.fireBlockStorageEvent(\"%s\", \"%s\", %s, %s)";
    private static final String WAIT_EVENT_FORMAT = "CloudifyExecutorUtils.waitFor(\"%s\", \"%s\", \"%s\")";
    private static final String IS_NODE_STARTED_FORMAT = "CloudifyExecutorUtils.isNodeStarted(context, \"%s\")";
    private static final String EXECUTE_PARALLEL_FORMAT = "CloudifyExecutorUtils.executeParallel(%s, %s)";
    private static final String EXECUTE_ASYNC_FORMAT = "CloudifyExecutorUtils.executeAsync(%s, %s)";
    private static final String EXECUTE_GROOVY_FORMAT = "CloudifyExecutorUtils.executeGroovy(context, \"%s\", %s, %s, \"%s\")";
    private static final String EXECUTE_SCRIPT_FORMAT = "CloudifyExecutorUtils.executeScript(context, \"%s\", %s, %s, \"%s\")";
    public static final String SHUTDOWN_COMMAND = "CloudifyExecutorUtils.shutdown()";
    public static final String DESTROY_COMMAND = "CloudifyUtils.destroy()";
    private static final String RETURN_COMMAND_FORMAT = "return %s";

    private static final String GET_INSTANCE_ATTRIBUTE_FORMAT = "CloudifyAttributesUtils.getAttribute(context, %s, %s, %s, %s)";
    private static final String GET_IP_FORMAT = "CloudifyAttributesUtils.getIp(context, %s, %s)";

    private static final String GET_OPERATION_OUTPUT_FORMAT = "CloudifyAttributesUtils.getOperationOutput(context, %s, %s, %s, %s)";

    private static final String GET_TOSCA_RELATIONSHIP_ENVS_FORMAT = "EnvironmentBuilder.getTOSCARelationshipEnvs(context, %s, %s, %s, %s, %s, %s)";
    private static final String TO_ABSOLUTE_PATH_FORMAT = "CloudifyUtils.toAbsolutePath(context, \"%s\")";

    private static final String FIRE_RELATIONSHIP_TRIGGER_EVENT = "CloudifyExecutorUtils.fireRelationshipEvent(\"%s\", \"%s\", \"%s\", \"%s\", \"%s\", \"%s\", %s)";

    @Resource
    private ApplicationContext applicationContext;

    /**
     * Copy all internal extensions files to the service recipe directory.
     *
     * @param servicePath Path to the service recipe directory.
     * @param deploymentId The id of the current deployment
     * @param setup TODO
     * @throws IOException In case of a copy failure.
     */
    public void copyInternalResources(Path servicePath, String deploymentId, ServiceSetup setup) throws IOException {
        for (String resource : SERVICE_RECIPE_RESOURCES) {
            // Files.copy(loadResourceFromClasspath("classpath:" + resource), servicePath.resolve(resource));
            // copy to the service directory base
            String resourceFinalPath = Paths.get(resource).getFileName().toString();
            this.copyResourceFromClasspath("classpath:" + resource, servicePath.resolve(resourceFinalPath));
        }

        // build veocity templates
        Map<String, String> properties = Maps.newHashMap();
        properties.put("deploymentId", setup.getDeploymentId());
        String logLevel = setup.getProviderDeploymentProperties().get(DeploymentPropertiesNames.LOG_LEVEL);
        properties.put("LOG_LEVEL", StringUtils.isNotBlank(logLevel) ? logLevel : "INFO");

        for (String resource : SERVICE_RECIPE_RESOURCES_VELOCITY_TEMP) {
            VelocityUtil.writeToOutputFile(loadResourceFromClasspath("classpath:velocity/" + resource + ".vm"),
                    servicePath.resolve(resource.concat(".groovy")), properties);
        }

        copyAdditionnalLibs(servicePath);
    }

    private void copyAdditionnalLibs(Path servicePath) throws IOException {
        FileSystem fs = null;
        try {
            URI uri = applicationContext.getResource(RECIPE_USM_LIB_DIR).getURI();
            String uriStr = uri.toString();
            Path path = null;
            if (uriStr.contains("!")) {
                String[] array = uriStr.split("!");
                fs = FileSystems.newFileSystem(URI.create(array[0]), new HashMap<String, Object>());
                path = fs.getPath(array[1]);
            } else {
                path = Paths.get(uri);
            }
            FileUtils.copyDirectory(path.toFile(), servicePath.resolve(RECIPE_USM_LIB_DIR).toFile());
        } finally {
            if (fs != null) {
                fs.close();
            }
        }
    }

    protected void copyResourceFromClasspath(String resource, Path target) throws IOException {
        FileSystem fs = null;
        try {
            URI uri = applicationContext.getResource(resource).getURI();
            String uriStr = uri.toString();
            Path path = null;
            if (uriStr.contains("!")) {
                String[] array = uriStr.split("!");
                fs = FileSystems.newFileSystem(URI.create(array[0]), new HashMap<String, Object>());
                path = fs.getPath(array[1]);
            } else {
                path = Paths.get(uri);
            }
            Files.copy(path, target);
        } finally {
            if (fs != null) {
                fs.close();
            }
        }
    }

    /**
     * Return the execution command for an artifact implementation, based on it artifact type.
     *
     * @param operationFQN The full qualified name of the artifact implementation: nodeId:interface:operation
     * @param artifact The artifact to process
     * @param varParamsMap The names of the vars to pass as params for the command. This assumes the var is defined before calling this
     *            command
     * @param stringParamsMap The string params to pass in the command.
     * @param expectedOutputs TODO
     * @param scriptPath Path to the script relative to the service root directory.
     * @param logLevel TODO
     * @param supportedArtifactTypes The supported artifacts types for the related operation
     * @return
     * @throws IOException
     */

    public String getCommandBasedOnArtifactType(final String operationFQN, final ImplementationArtifact artifact, Map<String, String> varParamsMap,
            Map<String, String> stringParamsMap, Map<String, Set<String>> expectedOutputs, String scriptPath, ProviderLogLevel logLevel,
            String... supportedArtifactTypes) throws IOException {

        if (ArrayUtils.isEmpty(supportedArtifactTypes) || ArrayUtils.contains(supportedArtifactTypes, artifact.getArtifactType())) {
            stringParamsMap.put(OPERATION_FQN, operationFQN);
            switch (artifact.getArtifactType()) {
            case AlienExtentedConstants.GROOVY_ARTIFACT_TYPE:
                return getGroovyCommand(scriptPath, varParamsMap, stringParamsMap, expectedOutputs, logLevel);
            case AlienExtentedConstants.SHELL_ARTIFACT_TYPE:
            case AlienExtentedConstants.BATCH_ARTIFACT_TYPE:
                return getScriptCommand(scriptPath, varParamsMap, stringParamsMap, expectedOutputs, logLevel);
            default:
                throw new PaaSDeploymentException("Operation implementation <" + operationFQN + "> is defined using an unsupported artifact type <"
                        + artifact.getArtifactType() + ">.");
            }
        }
        throw new PaaSDeploymentException("Operation implementation <" + operationFQN + "> is defined using an unsupported artifact type <"
                + artifact.getArtifactType() + ">.");
    }

    /**
     * Return the execution command for a groovy script as a string.
     *
     * @param groovyScriptRelativePath Path to the groovy script relative to the service root directory.
     * @param varParamsMap The names of the vars to pass as params for the command. This assumes the var is defined before calling this
     *            command
     * @param stringParamsMap The string params to pass in the command.
     * @param expectedOutputs List of expected outputs' names
     * @param logLevel TODO
     * @return The execution command.
     * @throws IOException
     */
    public String getGroovyCommand(String groovyScriptRelativePath, Map<String, String> varParamsMap, Map<String, String> stringParamsMap,
            Map<String, Set<String>> expectedOutputs, ProviderLogLevel logLevel) throws IOException {
        String formatedParams = formatParams(stringParamsMap, varParamsMap);
        String formatedOutputs = serializeForGroovyMap(expectedOutputs);
        ProviderLogLevel finalLogLevel = logLevel != null ? logLevel : ProviderLogLevel.INFO;
        return String.format(EXECUTE_GROOVY_FORMAT, groovyScriptRelativePath, formatedParams, formatedOutputs, finalLogLevel);
    }

    /**
     * Return the execution command for multiple groovy scripts as a string. The command are separated with a "&&" or "||" depending on the parameter passed
     *
     * @param groovyCommands string commands to process.
     * @return The execution command.
     */
    public String getMultipleGroovyCommand(String operator, String... groovyCommands) {
        StringBuilder commandBuilder = new StringBuilder();
        if (!ArrayUtils.isEmpty(groovyCommands)) {
            for (int i = 0; i < groovyCommands.length; i++) {
                commandBuilder.append(groovyCommands[i]);
                if (i < groovyCommands.length - 1) {
                    commandBuilder.append(" " + operator + " ");
                }
            }
        }
        return commandBuilder.toString();
    }

    /**
     * add "return" keyword on a groovy command
     *
     * @param groovyCommand the groovy command to process.
     * @return The execution command with "return" keyword.
     */
    public String getReturnGroovyCommand(String groovyCommand) {
        return String.format(RETURN_COMMAND_FORMAT, groovyCommand);
    }

    /**
     * Return the execution command for a script (bash or batch) as a string.
     *
     * @param scriptRelativePath Path to the script relative to the service root directory.
     * @param varParamsMap The names of the vars to pass as params for the command. This assumes the var is defined before calling this
     *            command
     * @param stringParamsMap The string params to pass in the command.
     * @param expectedOutputs List of expected outputs' names
     * @param logLevel TODO
     * @return The execution command.
     * @throws IOException
     */
    public String getScriptCommand(String scriptRelativePath, Map<String, String> varParamsMap, Map<String, String> stringParamsMap,
            Map<String, Set<String>> expectedOutputs, ProviderLogLevel logLevel) throws IOException {
        String formatedParams = formatParams(stringParamsMap, varParamsMap);
        String formatedOutputs = serializeForGroovyMap(expectedOutputs);
        ProviderLogLevel finalLogLevel = logLevel != null ? logLevel : ProviderLogLevel.INFO;
        return String.format(EXECUTE_SCRIPT_FORMAT, scriptRelativePath, formatedParams, formatedOutputs, finalLogLevel);
    }

    /**
     * Return a command to execute a number of scripts in parallel and then join for all the script to complete.
     *
     * @param groovyScripts The groovy scripts to execute in parallel.
     * @param otherScripts Other scripts (bash or batch) to execute in parallel.
     * @return The execution command to execute the scripts in parallel and then join for all the script to complete.
     */
    public String getParallelCommand(List<String> groovyScripts, List<String> otherScripts) {
        return String.format(EXECUTE_PARALLEL_FORMAT, serializeForGroovyCollection(groovyScripts), serializeForGroovyCollection(otherScripts));
    }

    /**
     * Return a command to execute a number of scripts in parallel.
     *
     * @param groovyScripts The groovy scripts to execute in parallel.
     * @param otherScripts Other scripts (bash or batch) to execute in parallel.
     * @return The execution command to execute the scripts in parallel.
     */
    public String getAsyncCommand(List<String> groovyScripts, List<String> otherScripts) {
        return String.format(EXECUTE_ASYNC_FORMAT, serializeForGroovyCollection(groovyScripts), serializeForGroovyCollection(otherScripts));
    }

    /**
     * Return the execution command to fire an event.
     *
     * @param eventLease event lease
     * @param The node that has a status change.
     * @param The new status for the node.
     *
     * @return The execution command.
     */
    public String getFireEventCommand(String nodeName, String status, Double eventLease) {
        return String.format(FIRE_EVENT_FORMAT, nodeName, status, eventLease);
    }

    /**
     * Return the execution command to fire a blockstorage event.
     *
     * @param eventLease event lease
     * @param The node that has a status change.
     * @param The new status for the node.
     *
     * @return The execution command.
     */
    public String getFireBlockStorageEventCommand(String nodeName, String status, String volumeId, Double eventLease) {
        return String.format(FIRE_BLOCKSTORAGE_EVENT_FORMAT, nodeName, status, volumeId, eventLease);
    }

    /**
     * Return the execution command to wait for a services.
     *
     * @param The name of the service from Cloudify point of view.
     * @param The node that has a status change.
     * @param The new status for the node.
     * @return The execution command.
     */
    public String getWaitEventCommand(String cloudifyService, String nodeName, String status) {
        return String.format(WAIT_EVENT_FORMAT, cloudifyService, nodeName, status);
    }

    /**
     * Return the execution command to check if a node is started.
     *
     * @param nodeName The node that has a status change.
     * @return The execution command.
     */
    public String getIsNodeStartedCommand(String nodeName) {
        return String.format(IS_NODE_STARTED_FORMAT, nodeName);
    }

    /**
     * Return the execution command to get an attribute from the context .
     *
     * @param attributeName
     * @param cloudifyServiceName
     * @param instanceId
     * @param eligibleNodeNames
     *            List of nodes names in which to check for the attribute (in general parents nodes of the main one)
     * @return
     */
    public String getAttributeCommand(String attributeName, String cloudifyServiceName, String instanceId, Collection<String> eligibleNodeNames) {
        cloudifyServiceName = formatString(cloudifyServiceName);
        attributeName = formatString(attributeName);
        String nodeNames = serializeForGroovyCollection(eligibleNodeNames);
        return String.format(GET_INSTANCE_ATTRIBUTE_FORMAT, cloudifyServiceName, instanceId, attributeName, nodeNames);
    }

    /**
     * Return the execution command to get an operation output from the context .
     *
     * @param formatedOutputName
     * @param eligibleNodeNames
     * @param cloudifyServiceName
     * @param instanceId
     * @return
     * @throws IOException
     */
    public String getOperationOutputCommand(String formatedOutputName, List<String> eligibleNodeNames, String cloudifyServiceName, String instanceId)
            throws IOException {
        cloudifyServiceName = formatString(cloudifyServiceName);
        formatedOutputName = formatString(formatedOutputName);
        String nodesnames = serializeForGroovyCollection(eligibleNodeNames);
        return String.format(GET_OPERATION_OUTPUT_FORMAT, cloudifyServiceName, instanceId, formatedOutputName, nodesnames);

    }

    /**
     * Return the execution command to get the IP address of a service .
     *
     * @param cloudifyServiceName
     * @param instanceId
     * @return
     */
    public String getIpCommand(String cloudifyServiceName, String instanceId) {
        cloudifyServiceName = formatString(cloudifyServiceName);
        return String.format(GET_IP_FORMAT, cloudifyServiceName, instanceId);
    }

    /**
     * Return the execution command to get the tosca relationships env vars
     *
     * @param name
     * @param baseValue
     * @param serviceName
     * @param attributes
     * @return
     * @throws IOException
     */
    public String getTOSCARelationshipEnvsCommand(String name, String baseValue, String serviceName, String instanceId, Map<String, String> attributes,
            Collection<String> eligibleNodesNames) throws IOException {
        name = formatString(name);
        baseValue = formatString(baseValue);
        serviceName = formatString(serviceName);
        String formatedParams = formatParams(attributes, null);
        String nodeNames = serializeForGroovyCollection(eligibleNodesNames);
        return String.format(GET_TOSCA_RELATIONSHIP_ENVS_FORMAT, name, baseValue, serviceName, instanceId, formatedParams, nodeNames);
    }

    /**
     * Return the execution command to get an absolute path (based on the service) of a relative one.
     *
     * @param relativePath
     * @return
     */
    public String getToAbsolutePathCommand(String relativePath) {
        if (StringUtils.isNotBlank(relativePath)) {
            return String.format(TO_ABSOLUTE_PATH_FORMAT, relativePath);
        }
        return null;
    }

    /**
     * Return the execution command to fire a relationship triggering event.
     *
     * @param nodeId
     * @param relationshipId
     * @param event
     * @param associatedNodeId
     * @param associatedNodeService
     * @param command
     * @param eventLease
     * @return
     */
    public String getFireRelationshipTriggerEvent(String nodeId, String relationshipId, String event, String associatedNodeId, String associatedNodeService,
            String command, Double eventLease) {
        return String.format(FIRE_RELATIONSHIP_TRIGGER_EVENT, nodeId, relationshipId, event, associatedNodeId, associatedNodeService, command, eventLease);
    }

    private String formatString(String toFormat) {
        return toFormat == null ? null : "\"" + toFormat + "\"";
    }

    private static void buildParamsAsString(Map<String, String> stringParamsMap, StringBuilder parametersSb) throws IOException {
        if (MapUtils.isNotEmpty(stringParamsMap)) {
            String serialized = (new ObjectMapper()).writeValueAsString(stringParamsMap);
            if (parametersSb.length() > 0) {
                parametersSb.append(", ");
            }
            parametersSb.append(serialized.substring(1, serialized.length() - 1));
        }
    }

    private static void buildParamsAsVar(Map<String, String> varParamsMap, StringBuilder parametersSb) {
        if (MapUtils.isNotEmpty(varParamsMap)) {
            for (Entry<String, String> entry : varParamsMap.entrySet()) {
                if (parametersSb.length() > 0) {
                    parametersSb.append(", ");
                }
                parametersSb.append("\"" + entry.getKey() + "\":" + entry.getValue());
            }
        }
    }

    private String formatParams(Map<String, String> stringParamsMap, Map<String, String> varParamsMap) throws IOException {
        StringBuilder parametersSb = new StringBuilder();
        buildParamsAsString(stringParamsMap, parametersSb);
        buildParamsAsVar(varParamsMap, parametersSb);
        return parametersSb.toString().trim().isEmpty() ? null : "[" + parametersSb.toString().trim() + "]";
    }

    private String serializeForGroovyCollection(Collection<String> collection) {
        String serialized = null;
        if (!CollectionUtils.isEmpty(collection)) {
            try {
                serialized = (new ObjectMapper()).writeValueAsString(collection);
            } catch (JsonProcessingException e) {
                log.warn("Serialization error of " + collection, e);
            }
        }
        return serialized;
    }

    private String serializeForGroovyMap(Map<? extends Object, ? extends Object> map) {
        String serialized = null;
        if (MapUtils.isNotEmpty(map)) {
            try {
                serialized = (new ObjectMapper()).writeValueAsString(map);
                serialized = "[" + serialized.substring(1, serialized.length() - 1) + "]";
            } catch (JsonProcessingException e) {
                log.warn("Serialization error of " + map, e);
            }
        }
        return serialized;
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

}