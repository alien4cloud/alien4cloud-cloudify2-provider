package alien4cloud.paas.cloudify2.generator;

import static alien4cloud.paas.cloudify2.generator.AlienExtentedConstants.CLOUDIFY_EXTENSIONS_INTERFACE_NAME;
import static alien4cloud.paas.cloudify2.generator.AlienExtentedConstants.CLOUDIFY_EXTENSIONS_LOCATOR_OPERATION_NAME;
import static alien4cloud.paas.cloudify2.generator.AlienExtentedConstants.CLOUDIFY_EXTENSIONS_START_DETECTION_OPERATION_NAME;
import static alien4cloud.paas.cloudify2.generator.AlienExtentedConstants.CLOUDIFY_EXTENSIONS_STOP_DETECTION_OPERATION_NAME;
import static alien4cloud.paas.cloudify2.generator.AlienExtentedConstants.CUSTOM_INTERFACE_NAME;
import static alien4cloud.paas.cloudify2.generator.RecipeGeneratorConstants.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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

import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.model.cloud.NetworkTemplate;
import alien4cloud.model.cloud.StorageTemplate;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import alien4cloud.model.components.IndexedToscaElement;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.topology.ScalingPolicy;
import alien4cloud.paas.IPaaSTemplate;
import alien4cloud.paas.cloudify2.DeploymentPropertiesNames;
import alien4cloud.paas.cloudify2.ServiceSetup;
import alien4cloud.paas.cloudify2.matcher.PaaSResourceMatcher;
import alien4cloud.paas.cloudify2.utils.CloudifyPaaSUtils;
import alien4cloud.paas.cloudify2.utils.VelocityUtil;
import alien4cloud.paas.exception.PaaSDeploymentException;
import alien4cloud.paas.exception.ResourceMatchingFailedException;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.plan.BuildPlanGenerator;
import alien4cloud.paas.plan.OperationCallActivity;
import alien4cloud.paas.plan.ParallelGateway;
import alien4cloud.paas.plan.ParallelJoinStateGateway;
import alien4cloud.paas.plan.RelationshipTriggerEvent;
import alien4cloud.paas.plan.StartEvent;
import alien4cloud.paas.plan.StateUpdateEvent;
import alien4cloud.paas.plan.StopEvent;
import alien4cloud.paas.plan.StopPlanGenerator;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.paas.plan.WorkflowStep;
import alien4cloud.tosca.normative.NormativeBlockStorageConstants;
import alien4cloud.tosca.normative.NormativeComputeConstants;
import alien4cloud.tosca.normative.NormativeNetworkConstants;
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
    private static final int DEFAULT_START_DETECTION_TIMEOUT = 600;

    private static final String START_DETECTION_SCRIPT_FILE_NAME = "startDetection";
    private static final String STOP_DETECTION_SCRIPT_FILE_NAME = "stopDetection";
    private static final String NAME_VALUE_TO_PARSE_KEWORD = "NAME_VALUE_TO_PARSE";
    private static final String IS_RETURN_TYPE = "isReturnType";

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
    private Path closureScriptDescriptorPath;
    private Path globalDetectionScriptDescriptorPath;

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
        closureScriptDescriptorPath = loadResourceFromClasspath("classpath:velocity/ClosureScriptDescriptor.vm");
        globalDetectionScriptDescriptorPath = loadResourceFromClasspath("classpath:velocity/GlobalDetectionScriptDescriptor.vm");
    }

    public Path generateRecipe(final String deploymentName, final String topologyId, final Map<String, PaaSNodeTemplate> nodeTemplates,
            final List<PaaSNodeTemplate> roots, DeploymentSetup setup) throws IOException {
        // cleanup/create the topology recipe directory
        Path recipePath = cleanupDirectory(topologyId);
        List<String> serviceIds = Lists.newArrayList();
        if (roots == null || roots.isEmpty()) {
            throw new PaaSDeploymentException("No compute found in topology for deployment " + deploymentName);
        }
        for (PaaSNodeTemplate root : roots) {
            ServiceSetup serviceSetup = new ServiceSetup();
            String nodeName = root.getId();
            serviceSetup.setComputeTemplate(getComputeTemplateOrDie(setup.getCloudResourcesMapping(), root));
            List<PaaSNodeTemplate> networkNodes = root.getNetworkNodes();
            if (networkNodes != null && !networkNodes.isEmpty()) {
                serviceSetup.setNetwork(getNetworkTemplateOrDie(setup.getNetworkMapping(), networkNodes.iterator().next()));
            }
            if (MapUtils.isNotEmpty(setup.getProviderDeploymentProperties())) {
                serviceSetup.setProviderDeploymentProperties(setup.getProviderDeploymentProperties());
            }
            PaaSNodeTemplate storageNode = root.getAttachedNode();
            if (storageNode != null) {
                serviceSetup.setStorage(getStorageTemplateOrDie(setup.getStorageMapping(), storageNode));
            }
            serviceSetup.setId(CloudifyPaaSUtils.serviceIdFromNodeTemplateId(nodeName));
            generateService(nodeTemplates, recipePath, root, serviceSetup);
            serviceIds.add(serviceSetup.getId());
        }

        generateApplicationDescriptor(recipePath, topologyId, deploymentName, serviceIds);

        return createZip(recipePath);
    }

    private StorageTemplate getStorageTemplateOrDie(Map<String, StorageTemplate> storageMapping, PaaSNodeTemplate storageNode) {
        paaSResourceMatcher.verifyNode(storageNode, NormativeBlockStorageConstants.BLOCKSTORAGE_TYPE);
        StorageTemplate storage = storageMapping.get(storageNode.getId());
        if (storage != null) {
            return storage;
        }
        throw new ResourceMatchingFailedException("Failed to find a storage for node <" + storageNode.getId() + ">");
    }

    private NetworkTemplate getNetworkTemplateOrDie(Map<String, NetworkTemplate> networkMapping, PaaSNodeTemplate networkNode) {
        paaSResourceMatcher.verifyNode(networkNode, NormativeNetworkConstants.NETWORK_TYPE);
        NetworkTemplate network = networkMapping.get(networkNode.getId());
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

    protected void generateService(final Map<String, PaaSNodeTemplate> nodeTemplates, final Path recipePath, final PaaSNodeTemplate computeNode,
            ServiceSetup setup) throws IOException {
        // find the compute template for this service
        String computeTemplate = paaSResourceMatcher.getTemplate(setup.getComputeTemplate());
        String networkName = null;
        String storageName = null;
        if (setup.getNetwork() != null) {
            networkName = paaSResourceMatcher.getNetwork(setup.getNetwork());
        }
        if (setup.getStorage() != null) {
            storageName = paaSResourceMatcher.getStorage(setup.getStorage());
        }
        log.info("Compute template ID for node <{}> is: [{}]", computeNode.getId(), computeTemplate);
        // create service directory
        Path servicePath = recipePath.resolve(setup.getId());
        Files.createDirectories(servicePath);

        RecipeGeneratorServiceContext context = new RecipeGeneratorServiceContext(nodeTemplates);
        context.setServiceId(setup.getId());
        context.setServicePath(servicePath);

        // copy internal static resources for the service
        commandGenerator.copyInternalResources(servicePath);

        // generate the properties file from the service node templates properties.
        generatePropertiesFile(context, computeNode);

        // copy artifacts for the nodes
        this.artifactCopier.copyAllArtifacts(context, computeNode);

        // generate cloudify init script
        generateInitScripts(context, computeNode, storageName);

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
        generateServiceDescriptor(context, setup.getId(), computeTemplate, networkName, computeNode.getScalingPolicy(), setup.getProviderDeploymentProperties()
                .get(DeploymentPropertiesNames.STARTDETECTION_TIMEOUT_INSECOND));
    }

    private void generateInitScripts(final RecipeGeneratorServiceContext context, final PaaSNodeTemplate computeNode, String storageName) throws IOException {
        String initCommand = "{}";
        List<String> executions = Lists.newArrayList();

        // process blockstorage init and startup
        storageScriptGenerator.generateInitStartUpStorageScripts(context, computeNode.getAttachedNode(), storageName, executions);

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
        Map<String, String> starDetectionCmds = Maps.newHashMap();
        generateExtendedOperationsCommand(context, computeNode, CLOUDIFY_EXTENSIONS_START_DETECTION_OPERATION_NAME, starDetectionCmds, true);

        // only register non null values
        for (Entry<String, String> entry : starDetectionCmds.entrySet()) {
            if (StringUtils.isNotBlank(entry.getValue())) {
                context.getStartDetectionCommands().put(entry.getKey(), entry.getValue());
            }
        }

        // generate a script that check every node of this service has reached the started state () fired the started event
        generateCheckStartedStateScript(context, starDetectionCmds.keySet());

        // now generate a global service startDetection
        manageDetectionStep(context, START_DETECTION_SCRIPT_FILE_NAME, SERVICE_START_DETECTION_COMMAND, context.getStartDetectionCommands(), AND_OPERATOR,
                globalDetectionScriptDescriptorPath);
    }

    private void manageStopDetection(RecipeGeneratorServiceContext context, PaaSNodeTemplate computeNode) throws IOException {
        // check stopDetection for each nodes and generate the command
        generateExtendedOperationsCommand(context, computeNode, CLOUDIFY_EXTENSIONS_STOP_DETECTION_OPERATION_NAME, context.getStopDetectionCommands(), false);

        // now generate a global service stopDetection
        manageDetectionStep(context, STOP_DETECTION_SCRIPT_FILE_NAME, SERVICE_STOP_DETECTION_COMMAND, context.getStopDetectionCommands(), OR_OPERATOR,
                globalDetectionScriptDescriptorPath);
    }

    private void manageProcessLocators(RecipeGeneratorServiceContext context, PaaSNodeTemplate computeNode) throws IOException {
        // check process locator for each nodes and generate the command
        generateExtendedOperationsCommand(context, computeNode, CLOUDIFY_EXTENSIONS_LOCATOR_OPERATION_NAME, context.getProcessLocatorsCommands(), false);
    }

    private void generateCheckStartedStateScript(final RecipeGeneratorServiceContext context, final Set<String> nodeIds) throws IOException {
        String fileName = "check-nodes-reached-started-state";
        List<String> commands = Lists.newArrayList();
        // generate a check started state command for each node
        for (String nodeId : nodeIds) {
            PaaSNodeTemplate node = context.getNodeTemplateById(nodeId);
            commands.add(commandGenerator.getIsNodeStartedCommand(CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(node), nodeId));
        }
        String command = commandGenerator.getMultipleGroovyCommand(AND_OPERATOR, commands.toArray(new String[0]));

        // write down the detection file in the node directory
        generateScriptWorkflow(context.getServicePath(), closureScriptDescriptorPath, fileName,
                Lists.newArrayList(commandGenerator.getReturnGroovyCommand(command)),
                MapUtil.newHashMap(new String[] { IS_RETURN_TYPE }, new Boolean[] { true }));
        // register the execution command to check the nodes states
        context.getStartDetectionCommands().put("checkState", commandGenerator.getGroovyCommand(fileName + ".groovy", null, null));
    }

    private void generateExtendedOperationsCommand(final RecipeGeneratorServiceContext context, PaaSNodeTemplate rootNode, String operationName,
            Map<String, String> commandsMap, boolean includeNullValues) throws IOException {
        String command = getOperationCommandFromInterface(context, rootNode, CLOUDIFY_EXTENSIONS_INTERFACE_NAME, operationName, new ExecEnvMaps());
        if (command != null || includeNullValues) {
            // here we register the command itself.
            commandsMap.put(rootNode.getId(), command);
        }

        // we do the same for children
        for (PaaSNodeTemplate childNode : rootNode.getChildren()) {
            generateExtendedOperationsCommand(context, childNode, operationName, commandsMap, includeNullValues);
        }
        if (rootNode.getAttachedNode() != null) {
            generateExtendedOperationsCommand(context, rootNode.getAttachedNode(), operationName, commandsMap, includeNullValues);
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
        Interface customInterface = nodeTemplate.getIndexedToscaElement().getInterfaces().get(CUSTOM_INTERFACE_NAME);
        if (customInterface != null) {
            Map<String, Operation> operations = customInterface.getOperations();
            for (Entry<String, Operation> entry : operations.entrySet()) {
                String commandUniqueName = CloudifyPaaSUtils.prefixWith(entry.getKey(), nodeTemplate.getId());
                addCustomCommand(context, nodeTemplate, CUSTOM_INTERFACE_NAME, commandUniqueName, entry);
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

    private void addCustomCommand(final RecipeGeneratorServiceContext context, final IPaaSTemplate<? extends IndexedArtifactToscaElement> nodeTemplate,
            String interfaceName, String uniqueName, Entry<String, Operation> entry) throws IOException {
        ExecEnvMaps envMaps = new ExecEnvMaps();
        // add the reserved env params
        envMaps.runtimes.put(NAME_VALUE_TO_PARSE_KEWORD, "args");

        // prepare and get the command
        String command = prepareAndGetCommand(context, nodeTemplate, interfaceName, entry.getKey(), envMaps, entry.getValue());

        log.debug("Configuring customCommand " + uniqueName + " with value " + command);
        context.getCustomCommands().put(uniqueName, command);
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
        if (workflowStep instanceof RelationshipTriggerEvent) {
            processRelationshipTriggerEvent(context, (RelationshipTriggerEvent) workflowStep, executions);
        } else if (workflowStep instanceof OperationCallActivity) {
            processOperationCallActivity(context, (OperationCallActivity) workflowStep, executions);
        } else if (workflowStep instanceof StateUpdateEvent) {
            StateUpdateEvent stateUpdateEvent = (StateUpdateEvent) workflowStep;
            // execute (if provided) the startDetection before firering the event started
            if (stateUpdateEvent.getState().equals(ToscaNodeLifecycleConstants.STARTED)) {
                addLoppedCommandToExecutions(context.getStartDetectionCommands().get(stateUpdateEvent.getElementId()), executions);
            }
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

    private void processRelationshipTriggerEvent(final RecipeGeneratorServiceContext context, final RelationshipTriggerEvent operationTriggerEvent,
            final List<String> executions) throws IOException {

        PaaSNodeTemplate paaSNodeTemplate = context.getNodeTemplateById(operationTriggerEvent.getNodeTemplateId());
        String uniqueName = CloudifyPaaSUtils.prefixWith(operationTriggerEvent.getSideOperationName(), operationTriggerEvent.getSideNodeTemplateId(),
                operationTriggerEvent.getRelationshipId());
        PaaSRelationshipTemplate paaSRelationshipTemplate = paaSNodeTemplate.getRelationshipTemplate(operationTriggerEvent.getRelationshipId());
        String sideServiceName = CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(context.getNodeTemplateById(operationTriggerEvent.getSideNodeTemplateId()));

        // first add the side operation as a custom command:
        // ex, if the operation is add_target, then add add_source as a customCommand for this node.
        addRelationshipCustomCommand(context, operationTriggerEvent, uniqueName, paaSRelationshipTemplate, sideServiceName);

        // then, trigger the main operation. Send an event to trigger it on the other pair node.
        // ex, if the operation is add_target, then trigger a custom command on the source node
        if (StringUtils.isNotBlank(operationTriggerEvent.getOperationName())) {
            String commandToTrigger = CloudifyPaaSUtils.prefixWith(operationTriggerEvent.getOperationName(), operationTriggerEvent.getNodeTemplateId(),
                    operationTriggerEvent.getRelationshipId());
            String command = commandGenerator.getFireRelationshipTriggerEvent(operationTriggerEvent.getNodeTemplateId(),
                    operationTriggerEvent.getRelationshipId(), operationTriggerEvent.getOperationName(), operationTriggerEvent.getSideNodeTemplateId(),
                    sideServiceName, commandToTrigger);
            executions.add(command);
        }
    }

    private void addRelationshipCustomCommand(final RecipeGeneratorServiceContext context, final RelationshipTriggerEvent operationTriggerEvent,
            String uniqueName, PaaSRelationshipTemplate paaSRelationshipTemplate, String sideServiceName) throws IOException {
        if (operationTriggerEvent.getSideOperationImplementationArtifact() != null) {
            ExecEnvMaps envMaps = new ExecEnvMaps();
            String serviceName = CloudifyPaaSUtils.cfyServiceNameFromNodeTemplate(context.getNodeTemplateById(operationTriggerEvent.getNodeTemplateId()));
            // add artifacts paths of the related node (only if the two member of the relationship are hosted on the same compute)
            // FIXME: manage it via get_artifact function
            if (serviceName.equals(sideServiceName)) {
                Map<String, String> copiedArtifactPathCmds = formatToAbsolutePathCmds(context.getNodeArtifactsPaths().get(
                        operationTriggerEvent.getSideNodeTemplateId()));
                if (copiedArtifactPathCmds != null) {
                    envMaps.runtimes.putAll(copiedArtifactPathCmds);
                }
            }
            String command = getCommandFromOperation(context, paaSRelationshipTemplate, operationTriggerEvent.getInterfaceName(),
                    operationTriggerEvent.getSideOperationName(), operationTriggerEvent.getSideOperationImplementationArtifact(),
                    operationTriggerEvent.getSideInputParameters(), "instanceId", envMaps);
            this.artifactCopier.copyImplementationArtifact(context, operationTriggerEvent.getCsarPath(),
                    operationTriggerEvent.getSideOperationImplementationArtifact(), paaSRelationshipTemplate.getIndexedToscaElement());
            context.getRelationshipCustomCommands().put(uniqueName, command);
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
        } else {
            generateRelationshipOperationCall(context, operationCall, executions, paaSNodeTemplate);
        }
    }

    private void addLoppedCommandToExecutions(final String command, final List<String> executions) {
        if (StringUtils.isNotBlank(command)) {
            // here, we should add a looped ( "while" wrapped) command to the executions of the node template
            executions.add(commandGenerator.getLoopedGroovyCommand(null, "!" + command));
        }
    }

    private void generateNodeOperationCall(final RecipeGeneratorServiceContext context, final OperationCallActivity operationCall,
            final List<String> executions, final PaaSNodeTemplate paaSNodeTemplate, final boolean isAsynchronous) throws IOException {
        this.artifactCopier.copyImplementationArtifact(context, operationCall.getCsarPath(), operationCall.getImplementationArtifact(),
                paaSNodeTemplate.getIndexedToscaElement());
        generateOperationCallCommand(context, operationCall, executions, isAsynchronous);
    }

    private void generateRelationshipOperationCall(final RecipeGeneratorServiceContext context, final OperationCallActivity operationCall,
            final List<String> executions, final PaaSNodeTemplate paaSNodeTemplate) throws IOException {
        PaaSRelationshipTemplate paaSRelationshipTemplate = paaSNodeTemplate.getRelationshipTemplate(operationCall.getRelationshipId());
        this.artifactCopier.copyImplementationArtifact(context, operationCall.getCsarPath(), operationCall.getImplementationArtifact(),
                paaSRelationshipTemplate.getIndexedToscaElement());
        generateOperationCallCommand(context, operationCall, executions, false);
    }

    private void generateOperationCallCommand(final RecipeGeneratorServiceContext context, final OperationCallActivity operationCall,
            final List<String> executions, final boolean isAsynchronous) throws IOException {

        ExecEnvMaps envMaps = new ExecEnvMaps();

        IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate = context.getNodeTemplateById(operationCall.getNodeTemplateId());
        if (operationCall.getRelationshipId() != null) {
            basePaaSTemplate = ((PaaSNodeTemplate) basePaaSTemplate).getRelationshipTemplate(operationCall.getRelationshipId());
        }

        // add artifacts paths of the node
        Map<String, String> copiedArtifactPathCmds = formatToAbsolutePathCmds(context.getNodeArtifactsPaths().get(operationCall.getNodeTemplateId()));
        if (copiedArtifactPathCmds != null) {
            envMaps.runtimes.putAll(copiedArtifactPathCmds);
        }

        // now call the operation script
        String command = getCommandFromOperation(context, basePaaSTemplate, operationCall.getInterfaceName(), operationCall.getOperationName(),
                operationCall.getImplementationArtifact(), operationCall.getInputParameters(), null, envMaps);

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
                    String elseCommand = alreadyStartedCommand;
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

    private Map<String, String> formatToAbsolutePathCmds(Map<String, String> paths) {
        if (paths == null) {
            return null;
        }
        Map<String, String> toReturnMap = Maps.newHashMap();
        for (Entry<String, String> entry : paths.entrySet()) {
            String linuxFormated = entry.getValue().replaceAll("\\\\", "/");
            toReturnMap.put(entry.getKey(), commandGenerator.getToAbsolutePathCommand(linuxFormated));
        }
        return toReturnMap;
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
            final String networkName, final ScalingPolicy scalingPolicy, String startDetectionTimeoutSec) throws IOException {
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
        properties.put(START_DETECTION_TIMEOUT_SEC, startDetectionTimeoutSec == null ? DEFAULT_START_DETECTION_TIMEOUT : startDetectionTimeoutSec);
        properties.put(SERVICE_CUSTOM_COMMANDS, context.getCustomCommands());
        properties.put(RELATIONSHIP_CUSTOM_COMMANDS, context.getRelationshipCustomCommands());
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