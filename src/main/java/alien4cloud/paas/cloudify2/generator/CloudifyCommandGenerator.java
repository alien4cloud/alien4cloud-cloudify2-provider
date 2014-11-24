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

import javax.annotation.Resource;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Generate properly formated commands for cloudify recipes.
 */
@Component
public class CloudifyCommandGenerator {
    private final static String[] SERVICE_RECIPE_RESOURCES = new String[] { "chmod-init.groovy", "CloudifyUtils.groovy", "GigaSpacesEventsManager.groovy",
            "CloudifyExecutorUtils.groovy" };

    private static final String FIRE_EVENT_FORMAT = "CloudifyExecutorUtils.fireEvent(\"%s\", \"%s\")";
    private static final String FIRE_BLOCKSTORAGE_EVENT_FORMAT = "CloudifyExecutorUtils.fireBlockStorageEvent(\"%s\", \"%s\", %s)";
    private static final String WAIT_EVENT_FORMAT = "CloudifyExecutorUtils.waitFor(\"%s\", \"%s\", \"%s\")";
    private static final String EXECUTE_PARALLEL_FORMAT = "CloudifyExecutorUtils.executeParallel(%s, %s)";
    private static final String EXECUTE_ASYNC_FORMAT = "CloudifyExecutorUtils.executeAsync(%s, %s)";
    private static final String EXECUTE_GROOVY_FORMAT = "CloudifyExecutorUtils.executeGroovy(\"%s\", %s)";
    private static final String EXECUTE_CLOSURE_GROOVY_FORMAT = "CloudifyExecutorUtils.executeGroovyInClosure(context, \"%s\", %s)";
    private static final String EXECUTE_BASH_FORMAT = "CloudifyExecutorUtils.executeBash(\"%s\")";
    public static final String SHUTDOWN_COMMAND = "CloudifyExecutorUtils.shutdown()";
    public static final String DESTROY_COMMAND = "CloudifyUtils.destroy()";
    private static final String EXECUTE_LOOPED_GROOVY_FORMAT = "while(%s){\n\t %s \n}";
    private static final String CONDITIONAL_IF_ELSE_GROOVY_FORMAT = "if(%s){\n\t%s\n}else{\n\t%s\n}";
    private static final String CONDITIONAL_IF_GROOVY_FORMAT = "if(%s){\n\t%s\n}";
    private static final String RETURN_COMMAND_FORMAT = "return %s";

    public static final String STORAGE_CREATE_VOLUME_COMMAND_FORMAT = "context.storage.createVolume(\"%s\")";
    public static final String STORAGE_ATTACH_VOLUME_COMMAND_FORMAT = "context.storage.attachVolume(\"%s\", \"%s\")";
    public static final String STORAGE_FORMAT_VOLUME_COMMAND_FORMAT = "context.storage.format(\"%s\", \"%s\")";
    public static final String STORAGE_MOUNT_VOLUME_COMMAND_FORMAT = "context.storage.mount(\"%s\", \"%s\")";
    public static final String STORAGE_UNMOUNT_VOLUME_COMMAND_FORMAT = "context.storage.unmount(\"%s\")";
    public static final String STORAGE_DETACH_VOLUME_COMMAND_FORMAT = "context.storage.detachVolume(\"%s\")";
    public static final String STORAGE_DELETE_VOLUME_COMMAND_FORMAT = "context.storage.delete(\"%s\")";

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
     * Return the execution command for a groovy script as a string.
     *
     * @param groovyScriptRelativePath Path to the groovy script relative to the service root directory.
     * @return The execution command.
     */
    public String getGroovyCommand(String groovyScriptRelativePath, String... parameters) {
        if (ArrayUtils.isEmpty(parameters)) {
            return String.format(EXECUTE_GROOVY_FORMAT, groovyScriptRelativePath, "null");
        }
        StringBuilder parametersSb = new StringBuilder();
        for (String parameter : parameters) {
            if (parametersSb.length() > 0) {
                parametersSb.append(", ");
            }
            parametersSb.append("\"" + parameter + "\"");
        }
        return String.format(EXECUTE_GROOVY_FORMAT, groovyScriptRelativePath, parametersSb.toString());
    }

    /**
     * Return the execution command for a groovy script as a string.
     *
     * @param groovyScriptRelativePath Path to the groovy script relative to the service root directory.
     *            * @param varParamNames The names of the vars to pass as params for the command. This assumes the var is defined before calling this
     *            command
     * @return The execution command.
     */
    public String getGroovyCommandWithParamsAsVar(String groovyScriptRelativePath, String... varParamNames) {
        if (ArrayUtils.isEmpty(varParamNames)) {
            return String.format(EXECUTE_GROOVY_FORMAT, groovyScriptRelativePath, "null");
        }
        StringBuilder parametersSb = new StringBuilder();
        for (String parameter : varParamNames) {
            if (parametersSb.length() > 0) {
                parametersSb.append(", ");
            }
            parametersSb.append(parameter);
        }
        return String.format(EXECUTE_GROOVY_FORMAT, groovyScriptRelativePath, parametersSb.toString());
    }

    /**
     * Return the execution command for a groovy script as a string.
     * The command is made such as it can be run in a closure.
     *
     * @param groovyScriptRelativePath Path to the groovy script relative to the service root directory.
     * @return The execution command.
     */
    public String getClosureGroovyCommand(String groovyScriptRelativePath, String... parameters) {
        if (ArrayUtils.isEmpty(parameters)) {
            return String.format(EXECUTE_CLOSURE_GROOVY_FORMAT, groovyScriptRelativePath, "null");
        }
        StringBuilder parametersSb = new StringBuilder();
        for (String parameter : parameters) {
            if (parametersSb.length() > 0) {
                parametersSb.append(", ");
            }
            parametersSb.append("\"" + parameter + "\"");
        }
        return String.format(EXECUTE_CLOSURE_GROOVY_FORMAT, groovyScriptRelativePath, parametersSb.toString());
    }

    /**
     * Return the execution command for a groovy script as a string.
     * The command is made such as it can be run in a closure.
     *
     * @param groovyScriptRelativePath Path to the groovy script relative to the service root directory.
     * @param varParamNames The names of the vars to pass as params for the command. This assumes the var is defined before calling this
     *            command
     * @return The execution command.
     */
    public String getClosureGroovyCommandWithParamsAsVar(String groovyScriptRelativePath, String... varParamNames) {
        if (ArrayUtils.isEmpty(varParamNames)) {
            return String.format(EXECUTE_CLOSURE_GROOVY_FORMAT, groovyScriptRelativePath, "null");
        }
        StringBuilder parametersSb = new StringBuilder();
        for (String parameter : varParamNames) {
            if (parametersSb.length() > 0) {
                parametersSb.append(", ");
            }
            parametersSb.append(parameter);
        }
        return String.format(EXECUTE_CLOSURE_GROOVY_FORMAT, groovyScriptRelativePath, parametersSb.toString());
    }

    /**
     *
     * transform a simple Groovy formated command to a closure groovy command (changes executeGroovy into executeGroovyInClosure )
     *
     * @param groovycommand
     * @return
     */
    public String fromSimpleToClosureGroovyCommand(String groovycommand) {
        if (groovycommand != null) {
            return groovycommand.replaceAll("executeGroovy(", "executeGroovyInClosure(");
        }
        return null;
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
     * Return the "while" wrapped execution command for a groovy script as a string.
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
     * Return the execution command for a bash script as a string.
     *
     * @param groovyScriptRelativePath Path to the bash script relative to the service root directory.
     * @return The execution command.
     */
    public String getBashCommand(String bashScriptRelativePath) {
        return String.format(EXECUTE_BASH_FORMAT, bashScriptRelativePath);
    }

    /**
     * Return a command to execute a number of scripts in parallel and then join for all the script to complete.
     *
     * @param groovyScripts The groovy scripts to execute in parallel.
     * @param bashScripts The bash scripts to execute in parallel.
     * @return The execution command to execute the scripts in parallel and then join for all the script to complete.
     */
    public String getParallelCommand(List<String> groovyScripts, List<String> bashScripts) {
        return String.format(EXECUTE_PARALLEL_FORMAT, generateParallelScriptsParameters(groovyScripts), generateParallelScriptsParameters(bashScripts));
    }

    /**
     * Return a command to execute a number of scripts in parallel.
     *
     * @param groovyScripts The groovy scripts to execute in parallel.
     * @param bashScripts The bash scripts to execute in parallel.
     * @return The execution command to execute the scripts in parallel.
     */
    public String getAsyncCommand(List<String> groovyScripts, List<String> bashScripts) {
        return String.format(EXECUTE_ASYNC_FORMAT, generateParallelScriptsParameters(groovyScripts), generateParallelScriptsParameters(bashScripts));
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

}