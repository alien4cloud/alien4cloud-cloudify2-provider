package alien4cloud.paas.cloudify2.generator;

import static alien4cloud.paas.cloudify2.generator.AlienExtentedConstants.*;
import static alien4cloud.paas.cloudify2.generator.RecipeGeneratorConstants.*;
import static alien4cloud.tosca.container.ToscaFunctionConstants.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.cloudifysource.dsl.internal.DSLUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import alien4cloud.component.model.IndexedToscaElement;
import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.model.cloud.Network;
import alien4cloud.paas.IPaaSTemplate;
import alien4cloud.paas.cloudify2.CloudifyPaaSUtils;
import alien4cloud.paas.cloudify2.VelocityUtil;
import alien4cloud.paas.cloudify2.matcher.PaaSResourceMatcher;
import alien4cloud.paas.exception.PaaSDeploymentException;
import alien4cloud.paas.exception.ResourceMatchingFailedException;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.plan.*;
import alien4cloud.tosca.container.model.NormativeComputeConstants;
import alien4cloud.tosca.container.model.NormativeNetworkConstants;
import alien4cloud.tosca.container.model.topology.ScalingPolicy;
import alien4cloud.tosca.model.Interface;
import alien4cloud.tosca.model.Operation;
import alien4cloud.utils.CollectionUtils;
import alien4cloud.utils.FileUtil;
import alien4cloud.utils.MapUtil;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * Utility class that generates a cloudify recipe from a TOSCA topology.
 */
@Slf4j
@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class RecipeGenerator extends AbstractCloudifyScriptGenerator {
    private static final int DEFAULT_INIT_MIN_INSTANCE = 1;
    private static final int DEFAULT_MAX_INSTANCE = 1;

    private static final String START_DETECTION_SCRIPT_FILE_NAME = "startDetection";
    private static final String STOP_DETECTION_SCRIPT_FILE_NAME = "stopDetection";
    private static final String RESEVED_ENV_KEYWORD = "NAME_VALUE_TO_PARSE";

    private Path recipeDirectoryPath;
    @Resource
    @Getter
    private PaaSResourceMatcher paaSResourceMatcher;
    @Resource
    @Getter
    private StorageScriptGenerator storageScriptGenerator;

    private Path applicationDescriptorPath;
    private Path serviceDescriptorPath;
    private Path scriptDescriptorPath;
    private Path detectionScriptDescriptorPath;

    @PostConstruct
    public void initialize() throws IOException {
        if (!Files.exists(recipeDirectoryPath)) {
            Files.createDirectories(recipeDirectoryPath);
        } else {
            FileUtil.delete(recipeDirectoryPath);
            Files.createDirectories(recipeDirectoryPath);
        }

        // initialize velocity template paths
        applicationDescriptorPath = loadResourceFromClasspath("classpath:velocity/ApplicationDescriptor.vm");
        serviceDescriptorPath = loadResourceFromClasspath("classpath:velocity/ServiceDescriptor.vm");
        scriptDescriptorPath = loadResourceFromClasspath("classpath:velocity/ScriptDescriptor.vm");
        detectionScriptDescriptorPath = loadResourceFromClasspath("classpath:velocity/detectionScriptDescriptor.vm");
    }

    public Path generateRecipe(final String deploymentName, final String topologyId, final Map<String, PaaSNodeTemplate> nodeTemplates,
            final List<PaaSNodeTemplate> roots, Map<String, ComputeTemplate> cloudResourcesMapping, Map<String, Network> networkMapping) throws IOException {
        // cleanup/create the topology recipe directory
        Path recipePath = cleanupDirectory(topologyId);
        List<String> serviceIds = Lists.newArrayList();

        for (PaaSNodeTemplate root : roots) {
            String nodeName = root.getId();
            ComputeTemplate template = getComputeTemplateOrDie(cloudResourcesMapping, root);
            Network network = null;
            PaaSNodeTemplate networkNode = root.getNetworkNode();
            if (networkNode != null) {
                network = getNetworkTemplateOrDie(networkMapping, networkNode);
            }
            String serviceId = CloudifyPaaSUtils.serviceIdFromNodeTemplateId(nodeName);
            generateService(nodeTemplates, recipePath, serviceId, root, template, network);
            serviceIds.add(serviceId);
        }

        generateApplicationDescriptor(recipePath, topologyId, deploymentName, serviceIds);

        return createZip(recipePath);
    }

    private Network getNetworkTemplateOrDie(Map<String, Network> networkMapping, PaaSNodeTemplate networkNode) {
        paaSResourceMatcher.verifyNode(networkNode, NormativeNetworkConstants.NETWORK_TYPE);
        Network network = networkMapping.get(networkNode.getId());
        if (network != null) {
            return network;
        }
        throw new ResourceMatchingFailedException("Failed to find a network for node <" + networkNode.getId() + ">");
    }

    private ComputeTemplate getComputeTemplateOrDie(Map<String, ComputeTemplate> cloudResourcesMapping, PaaSNodeTemplate node) {
        paaSResourceMatcher.verifyNode(node, NormativeComputeConstants.COMPUTE_TYPE);
        ComputeTemplate template = cloudResourcesMapping.get(node.getId());
        if (template != null) {
            return template;
        }
        throw new ResourceMatchingFailedException("Failed to find a compute template for node <" + node.getId() + ">");
    }

    /**
     * Find the name of the cloudify service that host the given node template.
     *
     * @param paaSNodeTemplate The node template for which to get the service name.
     * @return The id of the service that contains the node template.
     *
     * @throws PaaSDeploymentException if the node is not declared as hosted on a compute
     */
    private String cfyServiceNameFromNodeTemplateOrDie(final PaaSNodeTemplate paaSNodeTemplate) {
        try {
            return CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(paaSNodeTemplate);
        } catch (Exception e) {
            throw new PaaSDeploymentException("Failed to generate cloudify recipe.", e);
        }
    }

    public final Path deleteDirectory(final String deploymentId) throws IOException {
        Path topologyRecipeDirectory = recipeDirectoryPath.resolve(deploymentId);
        if (Files.exists(topologyRecipeDirectory)) {
            FileUtil.delete(topologyRecipeDirectory);
        }
        Path zipFilePath = getZipFilePath(topologyRecipeDirectory);
        if (Files.exists(zipFilePath)) {
            Files.delete(zipFilePath);
        }
        return topologyRecipeDirectory;
    }

    protected final Path cleanupDirectory(final String deploymentId) throws IOException {
        Path topologyRecipeDirectory = deleteDirectory(deploymentId);
        Files.createDirectories(topologyRecipeDirectory);
        return topologyRecipeDirectory;
    }

    protected Path createZip(final Path recipePath) throws IOException {
        // Generate application zip file.
        Path zipfilepath = Files.createFile(getZipFilePath(recipePath));

        log.debug("Creating application zip:  {}", zipfilepath);

        FileUtil.zip(recipePath, zipfilepath);

        log.debug("Application zip created");

        return zipfilepath;
    }

    private Path getZipFilePath(final Path recipePath) {
        return recipeDirectoryPath.resolve(recipePath.getFileName() + "-application.zip");
    }

    protected void generateService(final Map<String, PaaSNodeTemplate> nodeTemplates, final Path recipePath, final String serviceId,
            final PaaSNodeTemplate computeNode, ComputeTemplate template, Network network) throws IOException {
        // find the compute template for this service
        String computeTemplate = paaSResourceMatcher.getTemplate(template);
        String networkName = null;
        if (network != null) {
            networkName = paaSResourceMatcher.getNetwork(network);
        }
        log.info("Compute template ID for node <{}> is: [{}]", computeNode.getId(), computeTemplate);
        // create service directory
        Path servicePath = recipePath.resolve(serviceId);
        Files.createDirectories(servicePath);

        RecipeGeneratorServiceContext context = new RecipeGeneratorServiceContext(nodeTemplates);
        context.setServiceId(serviceId);
        context.setServicePath(servicePath);

        // copy internal static resources for the service
        commandGenerator.copyInternalResources(servicePath);

        // generate the properties file from the service node templates properties.
        generatePropertiesFile(context, computeNode);

        // copy artifacts for the nodes
        this.artifactCopier.copyAllArtifacts(context, computeNode);

        // generate cloudify init script
        generateInitScripts(context, computeNode);

        // generate cloudify global start detection script
        manageStartDetection(context, computeNode);

        // generate cloudify global stop detection script
        manageStopDetection(context, computeNode);

        // Generate startup life-cycle
        StartEvent startPlanStart = new BuildPlanGenerator().generate(computeNode);
        generateScript(startPlanStart, "start", context);

        // Generate the locators
        manageProcessLocators(context, computeNode);

        // Generate stop and deletion life-cycle
        StartEvent stopPlanStart = new StopPlanGenerator().generate(computeNode);
        generateScript(stopPlanStart, "stop", context);

        // Generate cloudify custom commands
        addCustomCommands(computeNode, context);

        // generate shutdown script
        generateShutdownScript(context, computeNode);

        // generate the service descriptor
        generateServiceDescriptor(context, serviceId, computeTemplate, networkName, computeNode.getScalingPolicy());
    }

    private void generateInitScripts(final RecipeGeneratorServiceContext context, final PaaSNodeTemplate computeNode) throws IOException {
        String initCommand = "{}";
        List<String> executions = Lists.newArrayList();

        // process blockstorage init and startup
        storageScriptGenerator.generateInitStartUpStorageScripts(context, computeNode.getAttachedNode(), executions);

        // generate the init script
        if (!executions.isEmpty()) {
            generateScriptWorkflow(context.getServicePath(), scriptDescriptorPath, INIT_LIFECYCLE, executions, null);
            initCommand = "\"" + INIT_LIFECYCLE + ".groovy\"";
        }
        context.getAdditionalProperties().put(INIT_COMMAND, initCommand);
    }

    private void generateShutdownScript(final RecipeGeneratorServiceContext context, final PaaSNodeTemplate computeNode) throws IOException {
        String shutdownCommand = "{}";
        List<String> executions = Lists.newArrayList();

        // process blockstorage shutdown and / or deletion
        storageScriptGenerator.generateShutdownStorageScript(context, computeNode.getAttachedNode(), executions);

        // generate the shutdown script
        if (!executions.isEmpty()) {
            generateScriptWorkflow(context.getServicePath(), scriptDescriptorPath, SHUTDOWN_LIFECYCLE, executions, null);
            shutdownCommand = "\"" + SHUTDOWN_LIFECYCLE + ".groovy\"";
        }
        context.getAdditionalProperties().put(SHUTDOWN_COMMAND, shutdownCommand);
    }

    private void manageStartDetection(final RecipeGeneratorServiceContext context, PaaSNodeTemplate computeNode) throws IOException {
        // check startDetection for each nodes and generate the command
        generateExtendedOperationsCommand(context, computeNode, CLOUDIFY_EXTENSIONS_START_DETECTION_OPERATION_NAME, context.getStartDetectionCommands());

        // now generate a global service startDetection
        manageDetectionStep(context, START_DETECTION_SCRIPT_FILE_NAME, SERVICE_START_DETECTION_COMMAND, context.getStartDetectionCommands(), AND_OPERATOR,
                detectionScriptDescriptorPath);
    }

    private void manageStopDetection(RecipeGeneratorServiceContext context, PaaSNodeTemplate computeNode) throws IOException {
        // check stopDetection for each nodes and generate the command
        generateExtendedOperationsCommand(context, computeNode, CLOUDIFY_EXTENSIONS_STOP_DETECTION_OPERATION_NAME, context.getStopDetectionCommands());

        // now generate a global service stopDetection
        manageDetectionStep(context, STOP_DETECTION_SCRIPT_FILE_NAME, SERVICE_STOP_DETECTION_COMMAND, context.getStopDetectionCommands(), OR_OPERATOR,
                detectionScriptDescriptorPath);
    }

    private void manageProcessLocators(RecipeGeneratorServiceContext context, PaaSNodeTemplate computeNode) throws IOException {
        // check process locator for each nodes and generate the command
        generateExtendedOperationsCommand(context, computeNode, CLOUDIFY_EXTENSIONS_LOCATOR_OPERATION_NAME, context.getProcessLocatorsCommands());
    }

    private void generateExtendedOperationsCommand(final RecipeGeneratorServiceContext context, PaaSNodeTemplate rootNode, String operationName,
            Map<String, String> commandsMap) throws IOException {
        generateNodeExtendedOperationCommand(context, rootNode, operationName, commandsMap);

        // we do the same for children
        for (PaaSNodeTemplate childNode : rootNode.getChildren()) {
            generateExtendedOperationsCommand(context, childNode, operationName, commandsMap);
        }
        if (rootNode.getAttachedNode() != null) {
            generateExtendedOperationsCommand(context, rootNode.getAttachedNode(), operationName, commandsMap);
        }
    }

    private void manageDetectionStep(final RecipeGeneratorServiceContext context, final String stepName, final String stepCommandName,
            final Map<String, String> stepCommandsMap, final String logicalOperator, final Path velocityDescriptorPath) throws IOException {

        if (MapUtils.isNotEmpty(stepCommandsMap)) {

            // get a formated command for all commands found: command1 && command2 && command3 or command1 || command2 || command3
            // this assumes that every command should return a boolean
            String detectioncommand = commandGenerator.getMultipleGroovyCommand(logicalOperator, stepCommandsMap.values().toArray(new String[0]));
            generateScriptWorkflow(context.getServicePath(), velocityDescriptorPath, stepName, null,
                    MapUtil.newHashMap(new String[] { SERVICE_DETECTION_COMMAND, "is" + stepName }, new Object[] { detectioncommand, true }));

            String detectionFilePath = stepName + ".groovy";
            String groovyCommand = commandGenerator.getGroovyCommand(detectionFilePath, null, null);
            String globalDectctionCommand = commandGenerator.getReturnGroovyCommand(groovyCommand);
            context.getAdditionalProperties().put(stepCommandName, globalDectctionCommand);
        }
    }

    private void addCustomCommands(final PaaSNodeTemplate nodeTemplate, final RecipeGeneratorServiceContext context) throws IOException {
        Interface customInterface = nodeTemplate.getIndexedNodeType().getInterfaces().get(CUSTOM_INTERFACE_NAME);
        if (customInterface != null) {
            Map<String, Operation> operations = customInterface.getOperations();
            for (Entry<String, Operation> entry : operations.entrySet()) {

                // add the reserved env params
                Map<String, String> runtimeEvalResults = Maps.newHashMap();
                runtimeEvalResults.put(RESEVED_ENV_KEYWORD, "args");

                // prepare and get the command
                String command = prepareAndGetCommand(context, nodeTemplate, CUSTOM_INTERFACE_NAME, entry.getKey(), runtimeEvalResults,
                        Maps.<String, String> newHashMap(), entry.getValue());

                String commandUniqueKey = CloudifyPaaSUtils.prefixWithTemplateId(entry.getKey(), nodeTemplate.getId());
                log.debug("Configuring customCommand " + commandUniqueKey + " with value " + command);
                context.getCustomCommands().put(commandUniqueKey, command);
            }
        }

        // process childs
        for (PaaSNodeTemplate child : nodeTemplate.getChildren()) {
            addCustomCommands(child, context);
        }

        // process attachedNodes
        if (nodeTemplate.getAttachedNode() != null) {
            addCustomCommands(nodeTemplate.getAttachedNode(), context);
        }
    }

    private void generateScript(final StartEvent startEvent, final String lifecycleName, final RecipeGeneratorServiceContext context) throws IOException {
        List<String> executions = Lists.newArrayList();

        WorkflowStep currentStep = startEvent.getNextStep();
        while (currentStep != null && !(currentStep instanceof StopEvent)) {
            processWorkflowStep(context, currentStep, executions);
            currentStep = currentStep.getNextStep();
        }
        if (lifecycleName.equals("stop")) {
            executions.add(CommandGenerator.SHUTDOWN_COMMAND);
            executions.add(CommandGenerator.DESTROY_COMMAND);
        }
        if (lifecycleName.equals("start")) {
            executions.add(CommandGenerator.DESTROY_COMMAND);
        }
        generateScriptWorkflow(context.getServicePath(), scriptDescriptorPath, lifecycleName, executions, null);
    }

    private void processWorkflowStep(final RecipeGeneratorServiceContext context, final WorkflowStep workflowStep, final List<String> executions)
            throws IOException {
        if (workflowStep instanceof OperationCallActivity) {
            processOperationCallActivity(context, (OperationCallActivity) workflowStep, executions);
        } else if (workflowStep instanceof StateUpdateEvent) {
            StateUpdateEvent stateUpdateEvent = (StateUpdateEvent) workflowStep;
            String command = commandGenerator.getFireEventCommand(stateUpdateEvent.getElementId(), stateUpdateEvent.getState());
            executions.add(command);
        } else if (workflowStep instanceof ParallelJoinStateGateway) {
            // generate wait for operations
            ParallelJoinStateGateway joinStateGateway = (ParallelJoinStateGateway) workflowStep;
            for (Map.Entry<String, String[]> nodeStates : joinStateGateway.getValidStatesPerElementMap().entrySet()) {
                // TODO supports multiple states
                String command = commandGenerator.getWaitEventCommand(cfyServiceNameFromNodeTemplateOrDie(context.getNodeTemplateById(nodeStates.getKey())),
                        nodeStates.getKey(), nodeStates.getValue()[0]);
                executions.add(command);
            }
        } else if (workflowStep instanceof ParallelGateway) {
            // generate multi-threaded installation management
            ParallelGateway gateway = (ParallelGateway) workflowStep;
            List<String> parallelScripts = Lists.newArrayList();
            for (WorkflowStep parallelStep : gateway.getParallelSteps()) {
                // create a script for managing this parallel workflow
                String scriptName = "parallel-" + UUID.randomUUID().toString();
                StartEvent parallelStartEvent = new StartEvent();
                parallelStartEvent.setNextStep(parallelStep);
                generateScript(parallelStartEvent, scriptName, context);
                parallelScripts.add(scriptName + ".groovy");
            }
            // now add an execution to the parallel thread management
            String command = commandGenerator.getParallelCommand(parallelScripts, null);
            executions.add(command);
        } else if (workflowStep instanceof StartEvent || workflowStep instanceof StopEvent) {
            log.debug("No action required to manage start or stop event.");
        } else {
            log.warn("Workflow step <" + workflowStep.getClass() + "> is not managed currently by cloudify PaaS Provider for alien 4 cloud.");
        }
    }

    private void processOperationCallActivity(final RecipeGeneratorServiceContext context, final OperationCallActivity operationCall,
            final List<String> executions) throws IOException {
        if (operationCall.getImplementationArtifact() == null) {
            return;
        }

        PaaSNodeTemplate paaSNodeTemplate = context.getNodeTemplateById(operationCall.getNodeTemplateId());
        if (operationCall.getRelationshipId() == null) {
            boolean isStartOperation = ToscaNodeLifecycleConstants.STANDARD.equals(operationCall.getInterfaceName())
                    && ToscaNodeLifecycleConstants.START.equals(operationCall.getOperationName());
            // generate the operation start itself
            generateNodeOperationCall(context, operationCall, executions, paaSNodeTemplate, isStartOperation);

            if (isStartOperation) {
                // add the startDetection command to the executions
                addLoppedCommandToExecutions(context.getStartDetectionCommands().get(operationCall.getNodeTemplateId()), executions);
            }
        } else {
            generateRelationshipOperationCall(context, operationCall, executions, paaSNodeTemplate);
        }
    }

    private void addLoppedCommandToExecutions(final String command, final List<String> executions) {
        if (executions != null && StringUtils.isNotBlank(command)) {
            // here, we should add a looped ( "while" wrapped) command to the executions of the node template
            executions.add(commandGenerator.getLoopedGroovyCommand(command, null));
        }
    }

    private void generateNodeExtendedOperationCommand(final RecipeGeneratorServiceContext context, final PaaSNodeTemplate paaSNodeTemplate,
            final String operationName, final Map<String, String> commandsMap) throws IOException {
        String command = getOperationCommandFromInterface(context, paaSNodeTemplate, CLOUDIFY_EXTENSIONS_INTERFACE_NAME, operationName, null, null);
        if (command != null) {
            // here we register the command itself.
            commandsMap.put(paaSNodeTemplate.getId(), command);
        }
    }

    private void generateNodeOperationCall(final RecipeGeneratorServiceContext context, final OperationCallActivity operationCall,
            final List<String> executions, final PaaSNodeTemplate paaSNodeTemplate, final boolean isAsynchronous) throws IOException {
        String relativePath = CloudifyPaaSUtils.getNodeTypeRelativePath(paaSNodeTemplate.getIndexedNodeType());
        this.artifactCopier.copyImplementationArtifact(context, operationCall.getCsarPath(), relativePath, operationCall.getImplementationArtifact());
        // we set as env var the node (self), and it host
        Map<String, String> nodesEnvVars = Maps.newHashMap();
        addNodeBaseEnvVars(paaSNodeTemplate, nodesEnvVars, SELF, PARENT, HOST);
        generateOperationCallCommand(context, relativePath, operationCall, null, nodesEnvVars, executions, isAsynchronous);
    }

    private Map<String, String> escapeForLinuxPath(Map<String, String> paths) {
        if (paths == null) {
            return null;
        }
        Map<String, String> toReturnMap = Maps.newHashMap();
        for (Entry<String, String> entry : paths.entrySet()) {
            toReturnMap.put(entry.getKey(), entry.getValue().replaceAll("\\\\", "/"));
        }
        return toReturnMap;
    }

    private void generateRelationshipOperationCall(final RecipeGeneratorServiceContext context, final OperationCallActivity operationCall,
            final List<String> executions, final PaaSNodeTemplate paaSNodeTemplate) throws IOException {
        PaaSRelationshipTemplate paaSRelationshipTemplate = paaSNodeTemplate.getRelationshipTemplate(operationCall.getRelationshipId());
        String relativePath = CloudifyPaaSUtils.getNodeTypeRelativePath(paaSRelationshipTemplate.getIndexedRelationshipType());
        this.artifactCopier.copyImplementationArtifact(context, operationCall.getCsarPath(), relativePath, operationCall.getImplementationArtifact());

        // we set as env var the source, target, source_host and target_host of the relationship
        Map<String, String> nodesEnvVars = Maps.newHashMap();
        nodesEnvVars.put(SOURCE, paaSRelationshipTemplate.getSource());
        nodesEnvVars.put(TARGET, paaSRelationshipTemplate.getRelationshipTemplate().getTarget());
        nodesEnvVars.put(SOURCE_HOST, CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(context.getNodeTemplateById(paaSRelationshipTemplate.getSource())));
        nodesEnvVars.put(TARGET_HOST,
                CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(context.getNodeTemplateById(paaSRelationshipTemplate.getRelationshipTemplate().getTarget())));

        generateOperationCallCommand(context, relativePath, operationCall, null, nodesEnvVars, executions, false);
    }

    private void generateOperationCallCommand(final RecipeGeneratorServiceContext context, final String relativePath,
            final OperationCallActivity operationCall, final Map<String, String> varParamNames, Map<String, String> stringParameters,
            final List<String> executions, final boolean isAsynchronous) throws IOException {

        IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate = context.getNodeTemplateById(operationCall.getNodeTemplateId());
        if (operationCall.getRelationshipId() != null) {
            basePaaSTemplate = ((PaaSNodeTemplate) basePaaSTemplate).getRelationshipTemplate(operationCall.getRelationshipId());
        }

        // add artifacts paths of the node (source node in case of a relationship )
        Map<String, String> copiedArtifactPath = escapeForLinuxPath(context.getNodeArtifactsPaths().get(operationCall.getNodeTemplateId()));
        stringParameters = CollectionUtils.merge(copiedArtifactPath, stringParameters, false);

        // now call the operation script
        String command = getCommandFromOperation(context, basePaaSTemplate, operationCall.getInterfaceName(), operationCall.getOperationName(), relativePath,
                operationCall.getImplementationArtifact(), varParamNames, stringParameters, operationCall.getInputParameters());

        if (isAsynchronous) {
            final String serviceId = CloudifyPaaSUtils.serviceIdFromNodeTemplateId(operationCall.getNodeTemplateId());
            String scriptName = "async-" + serviceId + "-" + operationCall.getOperationName() + "-" + UUID.randomUUID().toString();
            generateScriptWorkflow(context.getServicePath(), scriptDescriptorPath, scriptName, Lists.newArrayList(command), null);

            String asyncCommand = commandGenerator.getAsyncCommand(Lists.newArrayList(scriptName + ".groovy"), null);
            // if we are in the start lifecycle and there is either a startDetection or a stopDetection, then generate a conditional snippet.
            // so that we should only start the node if in restart case and the node is down (startDetetions(stopDetection) failure(success)), or if we are not
            // in the restart case
            if (operationCall.getOperationName().equals(ToscaNodeLifecycleConstants.START)) {
                String restartCondition = getRestartCondition(context, operationCall);
                String contextInstanceAttrRestart = CONTEXT_THIS_INSTANCE_ATTRIBUTES + ".restart";
                if (restartCondition != null) {
                    String trigger = contextInstanceAttrRestart + " != true || (" + restartCondition + ")";
                    // TODO: fire already started state instead
                    String alreadyStartedCommand = commandGenerator.getFireEventCommand(operationCall.getNodeTemplateId(), ToscaNodeLifecycleConstants.STARTED);
                    String elseCommand = alreadyStartedCommand.concat("\n\t").concat("return");
                    asyncCommand = commandGenerator.getConditionalSnippet(trigger, asyncCommand, elseCommand);
                } else {
                    // here we try to display a warning message for the restart case
                    log.warn("Node <{}> doesn't have neither startDetection, nor stopDetection lyfecycle event.", serviceId);
                    String warningTrigger = contextInstanceAttrRestart + " == true";
                    String warning = "println \"Neither startDetection nor stopDetetion found for <" + serviceId
                            + ">! will restart the node even if already started.\"";
                    String warningSnippet = commandGenerator.getConditionalSnippet(warningTrigger, warning, null);
                    executions.add(warningSnippet);
                }
            }
            executions.add(asyncCommand);
        } else {
            executions.add(command);
        }
    }

    private String getRestartCondition(final RecipeGeneratorServiceContext context, final OperationCallActivity operationCall) {
        String restartCondition = null;
        String startDetectionCommand = context.getStartDetectionCommands().get(operationCall.getNodeTemplateId());
        String instanceRestartContextAttr = CONTEXT_THIS_INSTANCE_ATTRIBUTES + ".restart";
        if (startDetectionCommand != null) {
            restartCondition = instanceRestartContextAttr + " == true && !" + startDetectionCommand;
        } else {
            String stopDetectionCommand = context.getStopDetectionCommands().get(operationCall.getNodeTemplateId());
            if (stopDetectionCommand != null) {
                restartCondition = instanceRestartContextAttr + " == true && " + stopDetectionCommand;
            }
        }
        return restartCondition;
    }

    private void generateServiceDescriptor(final RecipeGeneratorServiceContext context, final String serviceName, final String computeTemplate,
            final String networkName, final ScalingPolicy scalingPolicy) throws IOException {
        Path outputPath = context.getServicePath().resolve(context.getServiceId() + DSLUtils.SERVICE_DSL_FILE_NAME_SUFFIX);

        // configure and write the service descriptor thanks to velocity.
        HashMap<String, Object> properties = Maps.newHashMap();
        properties.put(SERVICE_NAME, serviceName);
        properties.put(SERVICE_COMPUTE_TEMPLATE_NAME, computeTemplate);
        properties.put(SERVICE_NETWORK_NAME, networkName);
        if (scalingPolicy != null) {
            properties.put(SERVICE_NUM_INSTANCES, scalingPolicy.getInitialInstances());
            properties.put(SERVICE_MIN_ALLOWED_INSTANCES, scalingPolicy.getMinInstances());
            properties.put(SERVICE_MAX_ALLOWED_INSTANCES, scalingPolicy.getMaxInstances());
        } else {
            properties.put(SERVICE_NUM_INSTANCES, DEFAULT_INIT_MIN_INSTANCE);
            properties.put(SERVICE_MIN_ALLOWED_INSTANCES, DEFAULT_INIT_MIN_INSTANCE);
            properties.put(SERVICE_MAX_ALLOWED_INSTANCES, DEFAULT_MAX_INSTANCE);
        }
        properties.put(SERVICE_CUSTOM_COMMANDS, context.getCustomCommands());
        for (Entry<String, String> entry : context.getAdditionalProperties().entrySet()) {
            properties.put(entry.getKey(), entry.getValue());
        }
        properties.put(LOCATORS, context.getProcessLocatorsCommands());

        VelocityUtil.writeToOutputFile(serviceDescriptorPath, outputPath, properties);
    }

    protected void generateApplicationDescriptor(final Path recipePath, final String topologyId, final String deploymentName, final List<String> serviceIds)
            throws IOException {
        // configure and write the application descriptor thanks to velocity.
        Path outputPath = recipePath.resolve(topologyId + DSLUtils.APPLICATION_DSL_FILE_NAME_SUFFIX);

        HashMap<String, Object> properties = Maps.newHashMap();
        properties.put(APPLICATION_NAME, deploymentName);
        properties.put(APPLICATION_SERVICES, serviceIds);

        VelocityUtil.writeToOutputFile(applicationDescriptorPath, outputPath, properties);
    }

    @Required
    @Value("${directories.alien}/cloudify")
    public void setRecipeDirectoryPath(final String path) {
        log.debug("Setting temporary path to {}", path);
        recipeDirectoryPath = Paths.get(path).toAbsolutePath();
    }
}