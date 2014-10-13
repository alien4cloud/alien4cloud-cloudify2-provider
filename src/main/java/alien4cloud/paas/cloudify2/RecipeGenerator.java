package alien4cloud.paas.cloudify2;

import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.AND_OPERATOR;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.APPLICATION_NAME;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.APPLICATION_SERVICES;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.CONTEXT_THIS_INSTANCE_ATTRIBUTES;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.FORMAT_VOLUME_COMMAND;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.GET_VOLUMEID_COMMAND;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.LOCATORS;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.OR_OPERATOR;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.PATH_KEY;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.POSTSTART_COMMAND;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.SCRIPTS;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.SCRIPT_LIFECYCLE;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.SERVICE_COMPUTE_TEMPLATE_NAME;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.SERVICE_CUSTOM_COMMANDS;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.SERVICE_DETECTION_COMMAND;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.SERVICE_MAX_ALLOWED_INSTANCES;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.SERVICE_MIN_ALLOWED_INSTANCES;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.SERVICE_NAME;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.SERVICE_NUM_INSTANCES;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.SERVICE_START_DETECTION_COMMAND;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.SERVICE_STOP_DETECTION_COMMAND;
import static alien4cloud.paas.cloudify2.RecipeGeneratorConstants.SHUTDOWN_COMMAND;

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
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import alien4cloud.component.model.IndexedToscaElement;
import alien4cloud.paas.exception.MissingPropertyException;
import alien4cloud.paas.exception.PaaSDeploymentException;
import alien4cloud.paas.exception.PaaSTechnicalException;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.plan.OperationCallActivity;
import alien4cloud.paas.plan.PaaSPlanGenerator;
import alien4cloud.paas.plan.ParallelGateway;
import alien4cloud.paas.plan.ParallelJoinStateGateway;
import alien4cloud.paas.plan.PlanGeneratorConstants;
import alien4cloud.paas.plan.StartEvent;
import alien4cloud.paas.plan.StateUpdateEvent;
import alien4cloud.paas.plan.StopEvent;
import alien4cloud.paas.plan.WorkflowStep;
import alien4cloud.tosca.container.model.NormativeBlockStorageConstants;
import alien4cloud.tosca.container.model.NormativeComputeConstants;
import alien4cloud.tosca.container.model.topology.ScalingPolicy;
import alien4cloud.tosca.container.model.type.ImplementationArtifact;
import alien4cloud.tosca.container.model.type.Interface;
import alien4cloud.tosca.container.model.type.Operation;
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
public class RecipeGenerator {
    private static final int DEFAULT_INIT_MIN_INSTANCE = 1;
    private static final int DEFAULT_MAX_INSTANCE = 1;

    private static final String DEFAULT_BLOCKSTORAGE_DEVICE = "/dev/vdb";
    private static final String DEFAULT_BLOCKSTORAGE_PATH = "/mountedStorage";
    private static final String DEFAULT_BLOCKSTORAGE_FS = "ext4";

    private static final String SHELL_ARTIFACT_TYPE = "tosca.artifacts.ShellScript";
    private static final String GROOVY_ARTIFACT_TYPE = "tosca.artifacts.GroovyScript";

    private static final String NODE_CUSTOM_INTERFACE_NAME = "custom";
    private static final String NODE_CLOUDIFY_EXTENSIONS_INTERFACE_NAME = "fastconnect.cloudify.extensions";
    private static final String CLOUDIFY_EXTENSIONS_START_DETECTION_OPERATION_NAME = "start_detection";
    private static final String CLOUDIFY_EXTENSIONS_STOP_DETECTION_OPERATION_NAME = "stop_detection";
    private static final String CLOUDIFY_EXTENSIONS_LOCATOR_OPERATION_NAME = "locator";

    private static final String START_DETECTION_SCRIPT_FILE_NAME = "startDetection";
    private static final String STOP_DETECTION_SCRIPT_FILE_NAME = "stopDetection";

    private Path recipeDirectoryPath;

    @Resource
    @Getter
    private ComputeTemplateMatcher computeTemplateMatcher;
    @Resource
    @Getter
    private StorageTemplateMatcher storageTemplateMatcher;
    @Resource
    private ApplicationContext applicationContext;
    @Resource
    private CloudifyCommandGenerator cloudifyCommandGen;
    @Resource
    private RecipeGeneratorArtifactCopier artifactCopier;
    @Resource
    private RecipePropertiesGenerator recipePropertiesGenerator;

    private Path applicationDescriptorPath;
    private Path serviceDescriptorPath;
    private Path scriptDescriptorPath;
    private Path detectionScriptDescriptorPath;
    private Path attachBlockStorageScriptDescriptorPath;
    private Path detachBlockStorageScriptDescriptorPath;

    @PostConstruct
    public void initialize() throws IOException {
        if (!Files.exists(recipeDirectoryPath)) {
            Files.createDirectories(recipeDirectoryPath);
        } else {
            FileUtil.delete(recipeDirectoryPath);
            Files.createDirectories(recipeDirectoryPath);
        }

        // initialize velocity template paths
        applicationDescriptorPath = recipePropertiesGenerator.loadResourceFromClasspath("classpath:velocity/ApplicationDescriptor.vm");
        serviceDescriptorPath = recipePropertiesGenerator.loadResourceFromClasspath("classpath:velocity/ServiceDescriptor.vm");
        scriptDescriptorPath = recipePropertiesGenerator.loadResourceFromClasspath("classpath:velocity/ScriptDescriptor.vm");
        detectionScriptDescriptorPath = recipePropertiesGenerator.loadResourceFromClasspath("classpath:velocity/detectionScriptDescriptor.vm");
        attachBlockStorageScriptDescriptorPath = recipePropertiesGenerator.loadResourceFromClasspath("classpath:velocity/attachBlockStorage.vm");
        detachBlockStorageScriptDescriptorPath = recipePropertiesGenerator.loadResourceFromClasspath("classpath:velocity/detachBlockStorage.vm");
    }

    public Path generateRecipe(final String deploymentName, final String topologyId, final Map<String, PaaSNodeTemplate> nodeTemplates, final List<PaaSNodeTemplate> roots)
            throws IOException {
        // cleanup/create the topology recipe directory
        Path recipePath = cleanupDirectory(topologyId);
        List<String> serviceIds = Lists.newArrayList();

        for (PaaSNodeTemplate root : roots) {
            String serviceName = root.getId();
            String serviceId = serviceIdFromNodeTemplateId(serviceName);
            generateService(nodeTemplates, recipePath, serviceId, serviceName, root);
            serviceIds.add(serviceId);
        }

        generateApplicationDescriptor(recipePath, topologyId, deploymentName, serviceIds);

        return createZip(recipePath);
    }

    public static String serviceIdFromNodeTemplateId(final String nodeTemplateId) {
        return nodeTemplateId.toLowerCase().replaceAll(" ", "-");
    }

    /**
     * Find the name of the cloudify service that host the given node template.
     *
     * @param nodeTemplate The node template for which to get the service name.
     * @return The id of the service that contains the node template.
     *
     * @throws PaaSDeploymentException if the node is not declared as hosted on a compute
     */
    private String serviceIdFromNodeTemplateOrDie(final PaaSNodeTemplate paaSNodeTemplate) {
        try {
            return serviceIfFromNodeTemplate(paaSNodeTemplate);
        } catch (Exception e) {
            throw new PaaSDeploymentException("Failed to generate cloudify recipe.", e);
        }
    }

    /**
     * Find the name of the cloudify service that host the given node template.
     *
     * @param nodeTemplate The node template for which to get the service name.
     * @return The id of the service that contains the node template.
     *
     */
    public String serviceIfFromNodeTemplate(final PaaSNodeTemplate paaSNodeTemplate) {
        PaaSNodeTemplate parent = paaSNodeTemplate;
        while (parent != null) {
            if (AlienUtils.isFromNodeType(parent.getIndexedNodeType(), NormativeComputeConstants.COMPUTE_TYPE)) {
                return serviceIdFromNodeTemplateId(parent.getId());
            }
            parent = parent.getParent();
        }
        throw new PaaSTechnicalException("Cannot get the service name: The node template <" + paaSNodeTemplate.getId()
                + "> is not declared as hosted on a compute.");
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

    protected void generateService(final Map<String, PaaSNodeTemplate> nodeTemplates, final Path recipePath, final String serviceId, final String serviceName,
            final PaaSNodeTemplate computeNode) throws IOException {
        // find the compute template for this service
        String computeTemplate = computeTemplateMatcher.getTemplate(computeNode);

        // create service directory
        Path servicePath = recipePath.resolve(serviceId);
        Files.createDirectories(servicePath);

        RecipeGeneratorServiceContext context = new RecipeGeneratorServiceContext(nodeTemplates);
        context.setServiceId(serviceId);
        context.setServicePath(servicePath);

        // copy internal static resources for the service
        cloudifyCommandGen.copyInternalResources(servicePath);

        // generate the properties file from the service node templates properties.
        recipePropertiesGenerator.generatePropertiesFile(context, computeNode);

        // copy artifacts for the nodes
        this.artifactCopier.copyAllArtifacts(context, computeNode);

        // check for blockStorage
        generateBlockStorageScript(context, computeNode);

        // Generate installation workflow scripts
        StartEvent creationPlanStart = PaaSPlanGenerator.buildNodeCreationPlan(computeNode);
        generateScript(creationPlanStart, "install", context);
        // Generate startup workflow scripts
        StartEvent startPlanStart = PaaSPlanGenerator.buildNodeStartPlan(computeNode);
        generateScript(startPlanStart, "start", context);

        StartEvent stopPlanStart = PaaSPlanGenerator.buildNodeStopPlan(computeNode);
        generateScript(stopPlanStart, "stop", context);

        // Generate custom commands
        addCustomCommands(computeNode, context);

        // generate global start detection script
        manageGlobalStartDetection(context);

        // generate global stop detection script
        manageGlobalStopDetection(context);

        // TODO generate specific cloudify supported interfaces (monitoring policies)

        // generate the service descriptor
        generateServiceDescriptor(context, serviceId, computeTemplate, computeNode.getScalingPolicy());
    }

    private void generateBlockStorageScript(final RecipeGeneratorServiceContext context, final PaaSNodeTemplate computeNode) throws IOException {
        PaaSNodeTemplate blockStorageNode = computeNode.getAttachedNode();
        String postStartCommand = "{}";
        String shutdownCommand = "{}";
        if (blockStorageNode != null) {
            Map<String, String> properties = blockStorageNode.getNodeTemplate().getProperties();
            if (MapUtils.isNotEmpty(properties)) {
                // case a volumeId is provided
                Map<String, String> velocityProps = Maps.newHashMap();
                // FIXME hack for the blockstorage to be in state started on the ui.
                // try manage it via plan generator
                velocityProps.put("creatingEvent", cloudifyCommandGen.getFireEventCommand(blockStorageNode.getId(),
                        PlanGeneratorConstants.STATE_CREATING));
                velocityProps.put("createdEvent", cloudifyCommandGen.getFireEventCommand(blockStorageNode.getId(),
                        PlanGeneratorConstants.STATE_CREATED));
                velocityProps.put("startingEvent", cloudifyCommandGen.getFireEventCommand(blockStorageNode.getId(),
                        PlanGeneratorConstants.STATE_STARTING));
                velocityProps.put("startedEvent", cloudifyCommandGen.getFireEventCommand(blockStorageNode.getId(),
                        PlanGeneratorConstants.STATE_STARTED));
                if (StringUtils.isNotBlank(properties.get(NormativeBlockStorageConstants.VOLUME_ID))) {
                    velocityProps.put(GET_VOLUMEID_COMMAND, "\"" + properties.get(NormativeBlockStorageConstants.VOLUME_ID) + "\"");

                } else if (StringUtils.isNotBlank(properties.get(NormativeBlockStorageConstants.SIZE))) {
                    // if not, case a size s provided
                    String storageTemplate = storageTemplateMatcher.getTemplate(blockStorageNode);
                    velocityProps.put(GET_VOLUMEID_COMMAND, cloudifyCommandGen.getCreateVolumeCommand(storageTemplate));
                    velocityProps.put(FORMAT_VOLUME_COMMAND, cloudifyCommandGen.getFormatVolumeCommand(DEFAULT_BLOCKSTORAGE_DEVICE, DEFAULT_BLOCKSTORAGE_FS));

                } else {
                    throw new MissingPropertyException("Neither <" + NormativeBlockStorageConstants.VOLUME_ID + ">, nor <" + NormativeBlockStorageConstants.SIZE
                            + "> properties are found in BlockStorage node <"
                            + blockStorageNode.getId() + ">. Please, provide one of them. ");
                }

                velocityProps.put(NormativeBlockStorageConstants.DEVICE, DEFAULT_BLOCKSTORAGE_DEVICE);
                velocityProps.put(PATH_KEY, DEFAULT_BLOCKSTORAGE_PATH);
                generateScriptWorkflow(context.getServicePath(), attachBlockStorageScriptDescriptorPath, "postStart", null, velocityProps);
                postStartCommand = "\"postStart.groovy\"";
                generateScriptWorkflow(context.getServicePath(), detachBlockStorageScriptDescriptorPath, "shutdown", null, null);
                shutdownCommand = "\"shutdown.groovy\"";
            } else {
                throw new MissingPropertyException("Neither <" + NormativeBlockStorageConstants.VOLUME_ID + ">, nor <" + NormativeBlockStorageConstants.SIZE
                        + "> properties are found in BlockStorage node <"
                        + blockStorageNode.getId() + ">. Please, provide one of them. ");
            }
        }
        context.getAdditionalProperties().put(POSTSTART_COMMAND, postStartCommand);
        context.getAdditionalProperties().put(SHUTDOWN_COMMAND, shutdownCommand);
    }

    private void manageGlobalStartDetection(final RecipeGeneratorServiceContext context) throws IOException {
        manageDetectionStep(context, START_DETECTION_SCRIPT_FILE_NAME, SERVICE_START_DETECTION_COMMAND, context.getStartDetectionCommands(), AND_OPERATOR,
                detectionScriptDescriptorPath);
    }

    private void manageGlobalStopDetection(final RecipeGeneratorServiceContext context) throws IOException {
        manageDetectionStep(context, STOP_DETECTION_SCRIPT_FILE_NAME, SERVICE_STOP_DETECTION_COMMAND, context.getStopDetectionCommands(), OR_OPERATOR,
                detectionScriptDescriptorPath);
    }

    private void manageDetectionStep(final RecipeGeneratorServiceContext context, final String stepName, final String stepCommandName,
            final Map<String, String> stepCommandsMap,
            final String commandsLogicalOperator, final Path velocityDescriptorPath) throws IOException {

        if (MapUtils.isNotEmpty(stepCommandsMap)) {

            // get a formated command for all commands found: command1 && command2 && command3 or command1 || command2 || command3
            // this assumes that every command should return a boolean
            String detectioncommand = cloudifyCommandGen.getMultipleGroovyCommand(commandsLogicalOperator, stepCommandsMap.values()
                    .toArray(new String[0]));
            generateScriptWorkflow(context.getServicePath(), velocityDescriptorPath, stepName, null,
                    MapUtil.newHashMap(new String[] { SERVICE_DETECTION_COMMAND, "is" + stepName }, new Object[] { detectioncommand, true }));

            String detectionFilePath = stepName + ".groovy";
            String groovyCommandForClosure = cloudifyCommandGen.getClosureGroovyCommand(detectionFilePath, null);
            String globalDectctionClosure = cloudifyCommandGen.getReturnGroovyCommand(groovyCommandForClosure);
            context.getAdditionalProperties().put(stepCommandName, globalDectctionClosure);
        }

    }


    private void addCustomCommands(final PaaSNodeTemplate nodeTemplate, final RecipeGeneratorServiceContext context) throws IOException {
        Interface customInterface = nodeTemplate.getIndexedNodeType().getInterfaces().get(NODE_CUSTOM_INTERFACE_NAME);
        if (customInterface != null) {
            // copy resources
            this.artifactCopier.copyDeploymentArtifacts(context, nodeTemplate.getCsarPath(), nodeTemplate.getId(), nodeTemplate.getIndexedNodeType(),
                    nodeTemplate.getNodeTemplate().getArtifacts());
            // add the custom commands for each operations
            Map<String, Operation> operations = customInterface.getOperations();
            for (Entry<String, Operation> entry : operations.entrySet()) {
                String key = entry.getKey();
                String relativePath = getNodeTypeRelativePath(nodeTemplate.getIndexedNodeType());
                // TODO copy implementation artifact.
                String artifactRef = relativePath + "/" + entry.getValue().getImplementationArtifact().getArtifactRef();
                String artifactType = entry.getValue().getImplementationArtifact().getArtifactType();
                String command;
                if (GROOVY_ARTIFACT_TYPE.equals(artifactType)) {
                    command = cloudifyCommandGen.getClosureGroovyCommandWithArrayParamsName(artifactRef, "args");
                } else {
                    throw new PaaSDeploymentException("Operation <" + nodeTemplate.getId() + "." + NODE_CUSTOM_INTERFACE_NAME + "."
                            + entry.getKey() + "> is defined using an unsupported artifact type <"
                            + artifactType + ">.");
                }

                if (log.isDebugEnabled()) {
                    log.debug("Configure customCommand " + key + " with value " + command);
                }
                context.getCustomCommands().put(key, command);
            }
        }
        for (PaaSNodeTemplate child : nodeTemplate.getChildren()) {
            addCustomCommands(child, context);
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
            executions.add(CloudifyCommandGenerator.SHUTDOWN_COMMAND);
        }
        executions.add(CloudifyCommandGenerator.DESTROY_COMMAND);
        generateScriptWorkflow(context.getServicePath(), scriptDescriptorPath, lifecycleName, executions, null);
    }

    private void processWorkflowStep(final RecipeGeneratorServiceContext context, final WorkflowStep workflowStep, final List<String> executions) throws IOException {
        if (workflowStep instanceof OperationCallActivity) {
            processOperationCallActivity(context, (OperationCallActivity) workflowStep, executions);
        } else if (workflowStep instanceof StateUpdateEvent) {
            StateUpdateEvent stateUpdateEvent = (StateUpdateEvent) workflowStep;
            String command = cloudifyCommandGen.getFireEventCommand(stateUpdateEvent.getElementId(), stateUpdateEvent.getState());
            executions.add(command);
        } else if (workflowStep instanceof ParallelJoinStateGateway) {
            // generate wait for operations
            ParallelJoinStateGateway joinStateGateway = (ParallelJoinStateGateway) workflowStep;
            for (Map.Entry<String, String[]> nodeStates : joinStateGateway.getValidStatesPerElementMap().entrySet()) {
                // TODO supports multiple states
                String command = cloudifyCommandGen.getWaitEventCommand(
                        serviceIdFromNodeTemplateOrDie(context.getNodeTemplateById(nodeStates.getKey())),
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
            String command = cloudifyCommandGen.getParallelCommand(parallelScripts, null);
            executions.add(command);
        } else if (workflowStep instanceof StartEvent || workflowStep instanceof StopEvent) {
            log.debug("No action required to manage start or stop event.");
        } else {
            log.warn("Workflow step <" + workflowStep.getClass() + "> is not managed currently by cloudify PaaS Provider for alien 4 cloud.");
        }
    }

    private void processOperationCallActivity(final RecipeGeneratorServiceContext context, final OperationCallActivity operationCall, final List<String> executions)
            throws IOException {
        if (operationCall.getImplementationArtifact() == null) {
            return;
        }

        PaaSNodeTemplate paaSNodeTemplate = context.getNodeTemplateById(operationCall.getNodeTemplateId());
        if (operationCall.getRelationshipId() == null) {
            boolean isStartOperation = PlanGeneratorConstants.NODE_LIFECYCLE_INTERFACE_NAME.equals(operationCall.getInterfaceName())
                    && PlanGeneratorConstants.START_OPERATION_NAME.equals(operationCall.getOperationName());
            if (isStartOperation) {
                // if there is a stop detection script for this node and the operation is start, then we should inject a stop detection here.
                generateNodeDetectionCommand(paaSNodeTemplate, CLOUDIFY_EXTENSIONS_STOP_DETECTION_OPERATION_NAME, context.getStopDetectionCommands(), true);
                // same for the start detection
                generateNodeDetectionCommand(paaSNodeTemplate, CLOUDIFY_EXTENSIONS_START_DETECTION_OPERATION_NAME, context.getStartDetectionCommands(), true);
                // now generate the operation start itself
                generateNodeOperationCall(context, operationCall, executions, paaSNodeTemplate, isStartOperation);
                // add the startDetection command to the executions
                addLoppedCommandToExecutions(context.getStartDetectionCommands().get(operationCall.getNodeTemplateId()), executions);
                // we also manage the locator if one is define for this node
                manageProcessLocator(paaSNodeTemplate, context.getProcessLocatorsCommands());
            } else {
                generateNodeOperationCall(context, operationCall, executions, paaSNodeTemplate, isStartOperation);
            }
        } else {
            generateRelationshipOperationCall(context, operationCall, executions, paaSNodeTemplate);
        }

        // TODO copy implementation artifact
    }

    private void addLoppedCommandToExecutions(final String command, final List<String> executions) {
        if (executions != null && StringUtils.isNotBlank(command)) {
            // here, we should add a looped ( "while" wrapped) command to the executions of the node template
            executions.add(cloudifyCommandGen.getLoopedGroovyCommand(command, null));
        }
    }

    private void manageProcessLocator(final PaaSNodeTemplate paaSNodeTemplate, final Map<String, String> processLocatorsCommands)
            throws IOException {
        String command = getExtendedOperationCommand(paaSNodeTemplate, CLOUDIFY_EXTENSIONS_LOCATOR_OPERATION_NAME, true);
        if (command != null) {
            processLocatorsCommands.put(paaSNodeTemplate.getId(), command);
        }

    }

    private void generateNodeDetectionCommand(final PaaSNodeTemplate paaSNodeTemplate, final String operationName,
            final Map<String, String> commandsMap, final boolean closureCommand) throws IOException {
        String command = getExtendedOperationCommand(paaSNodeTemplate, operationName, closureCommand);
        if (command != null) {
            // here we register the command itself.
            commandsMap.put(paaSNodeTemplate.getId(), command);
        }
    }

    private String getExtendedOperationCommand(final PaaSNodeTemplate nodeTemplate, final String operationName, final boolean closureCommand) {
        String command = null;
        Interface extensionsInterface = nodeTemplate.getIndexedNodeType().getInterfaces().get(NODE_CLOUDIFY_EXTENSIONS_INTERFACE_NAME);
        if (extensionsInterface != null) {
            Operation operation = extensionsInterface.getOperations().get(operationName);
            if (operation != null) {
                String[] parameters = new String[] { serviceIdFromNodeTemplateId(nodeTemplate.getId()), serviceIdFromNodeTemplateOrDie(nodeTemplate) };
                String relativePath = getNodeTypeRelativePath(nodeTemplate.getIndexedNodeType());
                // TODO copy implementation artifact.
                command = getCommandFromOperation(nodeTemplate.getId(), relativePath, operation.getImplementationArtifact(), closureCommand, parameters);
            }
        }

        return command;
    }

    private void generateNodeOperationCall(final RecipeGeneratorServiceContext context, final OperationCallActivity operationCall, final List<String> executions,
            final PaaSNodeTemplate paaSNodeTemplate, final boolean isAsynchronous) throws IOException {
        this.artifactCopier.copyDeploymentArtifacts(context, operationCall.getCsarPath(), paaSNodeTemplate.getId(),
                paaSNodeTemplate.getIndexedNodeType(), paaSNodeTemplate.getNodeTemplate().getArtifacts());
        String relativePath = getNodeTypeRelativePath(paaSNodeTemplate.getIndexedNodeType());
        Map<String, Path> copiedArtifactPath = context.getNodeArtifactsPaths().get(paaSNodeTemplate.getId());
        String[] parameters = new String[] { serviceIdFromNodeTemplateId(paaSNodeTemplate.getId()), serviceIdFromNodeTemplateOrDie(paaSNodeTemplate) };
        parameters = addCopiedPathsToParams(copiedArtifactPath, parameters);
        generateOperationCallCommand(context, relativePath, operationCall, parameters, executions, isAsynchronous);
    }

    private String[] addCopiedPathsToParams(Map<String, Path> copiedArtifactPath, String[] parameters) {
        if (MapUtils.isNotEmpty(copiedArtifactPath)) {
            List<String> tempList = Lists.newArrayList(parameters);
            for (Entry<String, Path> artifPath : copiedArtifactPath.entrySet()) {
                tempList.add(escapeForLinuxPath(artifPath));
            }
            parameters = tempList.toArray(new String[tempList.size()]);
        }
        return parameters;
    }

    private String escapeForLinuxPath(Entry<String, Path> pathEntry) {
        String pathStr = pathEntry.toString();
        return pathStr.replaceAll("\\\\", "/");
    }

    private void generateRelationshipOperationCall(final RecipeGeneratorServiceContext context, final OperationCallActivity operationCall, final List<String> executions,
            final PaaSNodeTemplate paaSNodeTemplate) throws IOException {
        PaaSRelationshipTemplate paaSRelationshipTemplate = paaSNodeTemplate.getRelationshipTemplate(operationCall.getRelationshipId());
        this.artifactCopier.copyDeploymentArtifacts(context, operationCall.getCsarPath(), null,
                paaSRelationshipTemplate.getIndexedRelationshipType(), null);
        String relativePath = getNodeTypeRelativePath(paaSRelationshipTemplate.getIndexedRelationshipType());

        String sourceNodeTemplateId = paaSRelationshipTemplate.getSource();
        String targetNodeTemplateId = paaSRelationshipTemplate.getRelationshipTemplate().getTarget();
        String sourceServiceId = serviceIdFromNodeTemplateOrDie(context.getNodeTemplateById(sourceNodeTemplateId));
        String targetServiceId = serviceIdFromNodeTemplateOrDie(context.getNodeTemplateById(targetNodeTemplateId));
        Map<String, Path> copiedArtifactPath = context.getNodeArtifactsPaths().get(sourceNodeTemplateId);
        String[] parameters = new String[] { serviceIdFromNodeTemplateId(sourceNodeTemplateId), sourceServiceId,
                serviceIdFromNodeTemplateId(targetNodeTemplateId), targetServiceId };
        parameters = addCopiedPathsToParams(copiedArtifactPath, parameters);
        generateOperationCallCommand(context, relativePath, operationCall, parameters, executions, false);
    }

    private void generateOperationCallCommand(final RecipeGeneratorServiceContext context, final String relativePath, final OperationCallActivity operationCall,
            final String[] parameters, final List<String> executions, final boolean isAsynchronous) throws IOException {
        // now call the operation script
        String command = getCommandFromOperation(operationCall.getNodeTemplateId(), relativePath, operationCall.getImplementationArtifact(), false, parameters);

        if (isAsynchronous) {
            final String serviceId = serviceIdFromNodeTemplateId(operationCall.getNodeTemplateId());
            String scriptName = "async-" + serviceId + "-" + operationCall.getOperationName() + "-" + UUID.randomUUID().toString();
            generateScriptWorkflow(context.getServicePath(), scriptDescriptorPath, scriptName, Lists.newArrayList(command), null);

            String asyncCommand = cloudifyCommandGen.getAsyncCommand(Lists.newArrayList(scriptName + ".groovy"), null);
            // if we are in the start lifecycle and there is either a startDetection or a stopDetection, then generate a conditional snippet.
            // so that we should only start the node if in restart case and the node is down (startDetetions(stopDetection) failure(success)), or if we are not
            // in the restart case
            if (operationCall.getOperationName().equals(PlanGeneratorConstants.START_OPERATION_NAME)) {
                String restartCondition = getRestartCondition(context, operationCall);
                String contextInstanceAttrRestart = CONTEXT_THIS_INSTANCE_ATTRIBUTES + ".restart";
                if (restartCondition != null) {
                    String trigger = contextInstanceAttrRestart + " != true || (" + restartCondition + ")";
                    // TODO: fire already started state instead
                    String alreadyStartedCommand = cloudifyCommandGen.getFireEventCommand(operationCall.getNodeTemplateId(),
                            PlanGeneratorConstants.STATE_STARTED);
                    String elseCommand = alreadyStartedCommand.concat("\n\t").concat("return");
                    asyncCommand = cloudifyCommandGen.getConditionalSnippet(trigger, asyncCommand, elseCommand);
                } else {
                    // here we try to display a warning message for the restart case
                    log.warn("Node <{}> doesn't have neither startDetection, nor stopDetection lyfecycle event.", serviceId);
                    String warningTrigger = contextInstanceAttrRestart + " == true";
                    String warning = "println \"Neither startDetection nor stopDetetion found for <" + serviceId
                            + ">! will restart the node even if already started.\"";
                    String warningSnippet = cloudifyCommandGen.getConditionalSnippet(warningTrigger, warning, null);
                    executions.add(warningSnippet);
                }
            }
            executions.add(asyncCommand);
        } else {
            executions.add(command);
        }
    }

    private String getCommandFromOperation(final String nodeId, final String relativePath, final ImplementationArtifact artifact, final boolean closureCommand,
            final String[] parameters) {
        if (StringUtils.isBlank(artifact.getArtifactRef())) {
            return null;
        }

        String scriptPath = relativePath + "/" + artifact.getArtifactRef();
        String command;
        // TODO copy implementation artifact.
        if (GROOVY_ARTIFACT_TYPE.equals(artifact.getArtifactType())) {
            command = closureCommand ? cloudifyCommandGen.getClosureGroovyCommand(scriptPath, parameters)
                    : cloudifyCommandGen.getGroovyCommand(scriptPath, parameters);
        } else if (SHELL_ARTIFACT_TYPE.equals(artifact.getArtifactType())) {
            command = cloudifyCommandGen.getBashCommand(scriptPath);
        } else {
            throw new PaaSDeploymentException("Operation <" + nodeId + "." + artifact.getInterfaceName() + "."
                    + artifact.getOperationName() + "> is defined using an unsupported artifact type <"
                    + artifact.getArtifactType() + ">.");
        }
        return command;
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

    /**
     * Compute the path of the node type of a node template relative to the service root directory.
     *
     * @param paaSNodeTemplate The PaaS Node template for which to generate and get it's directory relative path.
     * @return The relative path of the node tempalte's type artifacts in the service directory.
     */
    public static String getNodeTypeRelativePath(final IndexedToscaElement indexedToscaElement) {
        return indexedToscaElement.getElementId() + "-" + indexedToscaElement.getArchiveVersion();
    }

    private void generateScriptWorkflow(final Path servicePath, final Path velocityDescriptorPath, final String lifecycle, final List<String> executions,
            final Map<String, ? extends Object> additionalPropeties) throws IOException {
        Path outputPath = servicePath.resolve(lifecycle + ".groovy");

        HashMap<String, Object> properties = Maps.newHashMap();
        properties.put(SCRIPT_LIFECYCLE, lifecycle);
        properties.put(SCRIPTS, executions);
        if (additionalPropeties != null) {
            properties.putAll(additionalPropeties);
        }
        VelocityUtil.writeToOutputFile(velocityDescriptorPath, outputPath, properties);
    }

    private void generateServiceDescriptor(final RecipeGeneratorServiceContext context, final String serviceName, final String computeTemplate, final ScalingPolicy scalingPolicy)
            throws IOException {
        Path outputPath = context.getServicePath().resolve(context.getServiceId() + DSLUtils.SERVICE_DSL_FILE_NAME_SUFFIX);

        // configure and write the service descriptor thanks to velocity.
        HashMap<String, Object> properties = Maps.newHashMap();
        properties.put(SERVICE_NAME, serviceName);
        properties.put(SERVICE_COMPUTE_TEMPLATE_NAME, computeTemplate);
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

    protected void generateApplicationDescriptor(final Path recipePath, final String topologyId, final String deploymentName, final List<String> serviceIds) throws IOException {
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
