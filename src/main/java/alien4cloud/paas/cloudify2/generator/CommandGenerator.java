package alien4cloud.paas.cloudify2.generator;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.annotation.Resource;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import alien4cloud.model.components.ImplementationArtifact;
import alien4cloud.paas.exception.PaaSDeploymentException;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Generate properly formated commands for cloudify recipes.
 */
@Component
public class CommandGenerator {
    private final static String[] SERVICE_RECIPE_RESOURCES = new String[] { "chmod-init.groovy", "CloudifyUtils.groovy", "GigaSpacesEventsManager.groovy",
            "CloudifyExecutorUtils.groovy", "CloudifyAttributesUtils.groovy", "EnvironmentBuilder.groovy" };

    private static final String FIRE_EVENT_FORMAT = "CloudifyExecutorUtils.fireEvent(\"%s\", \"%s\")";
    private static final String FIRE_BLOCKSTORAGE_EVENT_FORMAT = "CloudifyExecutorUtils.fireBlockStorageEvent(\"%s\", \"%s\", %s)";
    private static final String WAIT_EVENT_FORMAT = "CloudifyExecutorUtils.waitFor(\"%s\", \"%s\", \"%s\")";
    private static final String EXECUTE_PARALLEL_FORMAT = "CloudifyExecutorUtils.executeParallel(%s, %s)";
    private static final String EXECUTE_ASYNC_FORMAT = "CloudifyExecutorUtils.executeAsync(%s, %s)";
    private static final String EXECUTE_GROOVY_FORMAT = "CloudifyExecutorUtils.executeGroovy(context, \"%s\", %s)";
    private static final String EXECUTE_SCRIPT_FORMAT = "CloudifyExecutorUtils.executeScript(\"%s\", %s)";
    public static final String SHUTDOWN_COMMAND = "CloudifyExecutorUtils.shutdown()";
    public static final String DESTROY_COMMAND = "CloudifyUtils.destroy()";
    private static final String EXECUTE_LOOPED_GROOVY_FORMAT = "while(%s){\n\t %s \n}";
    private static final String CONDITIONAL_IF_ELSE_GROOVY_FORMAT = "if(%s){\n\t%s\n}else{\n\t%s\n}";
    private static final String CONDITIONAL_IF_GROOVY_FORMAT = "if(%s){\n\t%s\n}";
    private static final String RETURN_COMMAND_FORMAT = "return %s";

    private static final String GET_INSTANCE_ATTRIBUTE_FORMAT = "CloudifyAttributesUtils.getAttribute(context, %s, %s, %s)";
    private static final String GET_IP_FORMAT = "CloudifyAttributesUtils.getIp(context, %s, %s)";

    private static final String GET_TOSCA_RELATIONSHIP_ENVS_FORMAT = "EnvironmentBuilder.getTOSCARelationshipEnvs(context, %s, %s, %s, %s)";

    @Resource
    private ApplicationContext applicationContext;

    /**
     * Copy all internal extensions files to the service recipe directory.
     *
     * @param servicePath Path to the service recipe directory.
     * @throws IOException In case of a copy failure.
     */
    public void copyInternalResources(Path servicePath) throws IOException {
        for (String resource : SERVICE_RECIPE_RESOURCES) {
            // Files.copy(loadResourceFromClasspath("classpath:" + resource), servicePath.resolve(resource));
            this.copyResourceFromClasspath("classpath:" + resource, servicePath.resolve(resource));
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
     * @param operationFQN The full qualified name of the artifact implementation: nodeId.interface.operation
     * @param artifact The artifact to process
     * @param varParamsMap The names of the vars to pass as params for the command. This assumes the var is defined before calling this
     *            command
     * @param stringParamsMap The string params to pass in the command.
     * @param scriptPath Path to the script relative to the service root directory.
     * @param supportedArtifactTypes The supported artifacts types for the related operation
     * @return
     * @throws IOException
     */

    public String getCommandBasedOnArtifactType(final String operationFQN, final ImplementationArtifact artifact, Map<String, String> varParamsMap,
            Map<String, String> stringParamsMap, String scriptPath, String... supportedArtifactTypes) throws IOException {

        if (ArrayUtils.isEmpty(supportedArtifactTypes) || ArrayUtils.contains(supportedArtifactTypes, artifact.getArtifactType())) {
            switch (artifact.getArtifactType()) {
                case AlienExtentedConstants.GROOVY_ARTIFACT_TYPE:
                    return getGroovyCommand(scriptPath, varParamsMap, stringParamsMap);
                case AlienExtentedConstants.SHELL_ARTIFACT_TYPE:
                case AlienExtentedConstants.BATCH_ARTIFACT_TYPE:
                    return getScriptCommand(scriptPath, varParamsMap, stringParamsMap);
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
     * @return The execution command.
     * @throws IOException
     */
    public String getGroovyCommand(String groovyScriptRelativePath, Map<String, String> varParamsMap, Map<String, String> stringParamsMap) throws IOException {
        String formatedParams = formatParams(stringParamsMap, varParamsMap);
        return String.format(EXECUTE_GROOVY_FORMAT, groovyScriptRelativePath, formatedParams);
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
     * <p>
     * Return the "while" wrapped execution command for a groovy script as a string.
     * </p>
     *
     * <pre>
     * getLoopedGroovyCommand("command", loopCondition) ==> while(loopCondition){ command }
     * </pre>
     *
     * @param groovyCommand the groovy command to wrap by the "while" loop.
     * @param loopCondition the condition to satisfy to continue the loop.
     * @return The looped execution command.
     */
    public String getLoopedGroovyCommand(String groovyCommand, String loopCondition) {
        if (StringUtils.isBlank(loopCondition)) {
            return String.format(EXECUTE_LOOPED_GROOVY_FORMAT, "!" + groovyCommand, "");
        } else {
            return String.format(EXECUTE_LOOPED_GROOVY_FORMAT, loopCondition, groovyCommand);
        }
    }

    /**
     * Return a conditional snippet
     *
     * @param condition The condition to satisfy
     * @param ifCommand The command to execute if satisfy
     * @param elseCommand Optional command to execute if not satisfy
     * @return a string representing the conditional snippet
     */
    public String getConditionalSnippet(String condition, String ifCommand, String elseCommand) {
        if (StringUtils.isBlank(condition) || ifCommand == null) {
            return null;
        }
        return StringUtils.isNotBlank(elseCommand) ? String.format(CONDITIONAL_IF_ELSE_GROOVY_FORMAT, condition, ifCommand, elseCommand) : String.format(
                CONDITIONAL_IF_GROOVY_FORMAT, condition, ifCommand);
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
     * @return The execution command.
     * @throws IOException
     */
    public String getScriptCommand(String scriptRelativePath, Map<String, String> varParamsMap, Map<String, String> stringParamsMap) throws IOException {
        String formatedParams = formatParams(stringParamsMap, varParamsMap);
        return String.format(EXECUTE_SCRIPT_FORMAT, scriptRelativePath, formatedParams);
    }

    /**
     * Return a command to execute a number of scripts in parallel and then join for all the script to complete.
     *
     * @param groovyScripts The groovy scripts to execute in parallel.
     * @param otherScripts Other scripts (bash or batch) to execute in parallel.
     * @return The execution command to execute the scripts in parallel and then join for all the script to complete.
     */
    public String getParallelCommand(List<String> groovyScripts, List<String> otherScripts) {
        return String.format(EXECUTE_PARALLEL_FORMAT, generateParallelScriptsParameters(groovyScripts), generateParallelScriptsParameters(otherScripts));
    }

    /**
     * Return a command to execute a number of scripts in parallel.
     *
     * @param groovyScripts The groovy scripts to execute in parallel.
     * @param otherScripts Other scripts (bash or batch) to execute in parallel.
     * @return The execution command to execute the scripts in parallel.
     */
    public String getAsyncCommand(List<String> groovyScripts, List<String> otherScripts) {
        return String.format(EXECUTE_ASYNC_FORMAT, generateParallelScriptsParameters(groovyScripts), generateParallelScriptsParameters(otherScripts));
    }

    private String generateParallelScriptsParameters(List<String> scripts) {
        StringBuilder scriptBuilder = new StringBuilder("[");
        if (scripts != null) {
            for (String script : scripts) {
                if (scriptBuilder.length() > 1) {
                    scriptBuilder.append(", ");
                }
                scriptBuilder.append("\"" + script + "\"");
            }
        }
        scriptBuilder.append("]");
        return scriptBuilder.toString();
    }

    /**
     * Return the execution command to fire an event.
     *
     * @param The node that has a status change.
     * @param The new status for the node.
     * @return The execution command.
     */
    public String getFireEventCommand(String nodeName, String status) {
        return String.format(FIRE_EVENT_FORMAT, nodeName, status);
    }

    /**
     * Return the execution command to fire a blockstorage event.
     *
     * @param The node that has a status change.
     * @param The new status for the node.
     * @return The execution command.
     */
    public String getFireBlockStorageEventCommand(String nodeName, String status, String volumeId) {
        return String.format(FIRE_BLOCKSTORAGE_EVENT_FORMAT, nodeName, status, volumeId);
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
     * Return the execution command to get an attribute from the context .
     *
     * @param attributeName
     * @param cloudifyServiceName
     * @param instanceId
     * @return
     */
    public String getAttributeCommand(String attributeName, String cloudifyServiceName, String instanceId) {
        cloudifyServiceName = formatString(cloudifyServiceName);
        attributeName = formatString(attributeName);
        instanceId = formatString(instanceId);
        return String.format(GET_INSTANCE_ATTRIBUTE_FORMAT, cloudifyServiceName, instanceId, attributeName);
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
        instanceId = formatString(instanceId);
        return String.format(GET_IP_FORMAT, cloudifyServiceName, instanceId);
    }

    public String getTOSCARelationshipEnvsCommand(String name, String baseValue, String serviceName, Map<String, String> attributes) throws IOException {
        name = formatString(name);
        baseValue = formatString(baseValue);
        serviceName = formatString(serviceName);
        String formatedParams = formatParams(attributes, null);
        return String.format(GET_TOSCA_RELATIONSHIP_ENVS_FORMAT, name, baseValue, serviceName, formatedParams);
    }

    private String formatString(String serviceName) {
        return serviceName == null ? null : "\"" + serviceName + "\"";
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

}