package alien4cloud.paas.cloudify2.generator;

import static alien4cloud.paas.cloudify2.generator.RecipeGeneratorConstants.*;
import static alien4cloud.tosca.normative.ToscaFunctionConstants.*;

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

import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.model.cloud.Network;
import alien4cloud.model.components.IOperationParameter;
import alien4cloud.model.components.ImplementationArtifact;
import alien4cloud.model.components.IndexedToscaElement;
import alien4cloud.model.components.Interface;
import alien4cloud.model.components.Operation;
import alien4cloud.model.topology.ScalingPolicy;
import alien4cloud.paas.IPaaSTemplate;
import alien4cloud.paas.cloudify2.CloudifyPaaSUtils;
import alien4cloud.paas.cloudify2.VelocityUtil;
import alien4cloud.paas.cloudify2.funtion.FunctionProcessor;
import alien4cloud.paas.cloudify2.matcher.PaaSResourceMatcher;
import alien4cloud.paas.cloudify2.matcher.StorageTemplateMatcher;
import alien4cloud.paas.exception.PaaSDeploymentException;
import alien4cloud.paas.exception.ResourceMatchingFailedException;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.plan.BuildPlanGenerator;
import alien4cloud.paas.plan.OperationCallActivity;
import alien4cloud.paas.plan.ParallelGateway;
import alien4cloud.paas.plan.ParallelJoinStateGateway;
import alien4cloud.paas.plan.StartEvent;
import alien4cloud.paas.plan.StateUpdateEvent;
import alien4cloud.paas.plan.StopEvent;
import alien4cloud.paas.plan.StopPlanGenerator;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.paas.plan.WorkflowStep;
import alien4cloud.tosca.ToscaUtils;
import alien4cloud.tosca.normative.AlienCustomTypes;
import alien4cloud.tosca.normative.NormativeBlockStorageConstants;
import alien4cloud.utils.CollectionUtils;
import alien4cloud.utils.FileUtil;
import alien4cloud.utils.MapUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final String DEFAULT_BLOCKSTORAGE_LOCATION = "/mountedStorage";
    private static final String DEFAULT_BLOCKSTORAGE_FS = "ext4";
    private static final String VOLUME_ID_VAR = "volumeId";

    private static final String FS = "file_system";

    private static final String SHELL_ARTIFACT_TYPE = "tosca.artifacts.ShellScript";
    private static final String GROOVY_ARTIFACT_TYPE = "tosca.artifacts.GroovyScript";

    private static final String NODE_CUSTOM_INTERFACE_NAME = "custom";
    private static final String NODE_CLOUDIFY_EXTENSIONS_INTERFACE_NAME = "fastconnect.cloudify.extensions";
    private static final String CLOUDIFY_EXTENSIONS_START_DETECTION_OPERATION_NAME = "start_detection";
    private static final String CLOUDIFY_EXTENSIONS_STOP_DETECTION_OPERATION_NAME = "stop_detection";
    private static final String CLOUDIFY_EXTENSIONS_LOCATOR_OPERATION_NAME = "locator";

    private static final String START_DETECTION_SCRIPT_FILE_NAME = "startDetection";
    private static final String STOP_DETECTION_SCRIPT_FILE_NAME = "stopDetection";
    private static final String INIT_STORAGE_SCRIPT_FILE_NAME = "initStorage";

    private static final String RESEVED_ENV_KEYWORD = "NAME_VALUE_TO_PARSE";

    private String STORAGE_STARTUP_FILE_NAME = "startupBlockStorage";
    private String DEFAULT_STORAGE_CREATE_FILE_NAME = "createAttachStorage";
    private String DEFAULT_STORAGE_MOUNT_FILE_NAME = "formatMountStorage";
    private String DEFAULT_STORAGE_UNMOUNT_FILE_NAME = "unmountDeleteStorage";
    private String STORAGE_SHUTDOWN_FILE_NAME = "shutdownBlockStorage";

    private Path recipeDirectoryPath;
    private ObjectMapper jsonMapper;

    @Resource
    @Getter
    private PaaSResourceMatcher paaSResourceMatcher;
    @Resource
    @Getter
    private StorageTemplateMatcher storageTemplateMatcher;
    @Resource
    private CloudifyCommandGenerator cloudifyCommandGen;
    @Resource
    private RecipeGeneratorArtifactCopier artifactCopier;
    @Resource
    private RecipePropertiesGenerator recipePropertiesGenerator;
    @Resource
    private FunctionProcessor funtionProcessor;

    private Path applicationDescriptorPath;
    private Path serviceDescriptorPath;
    private Path scriptDescriptorPath;
    private Path detectionScriptDescriptorPath;
    private Path createAttachBlockStorageScriptDescriptorPath;
    private Path formatMountBlockStorageScriptDescriptorPath;
    private Path startupBlockStorageScriptDescriptorPath;
    private Path unmountDeleteBlockStorageSCriptDescriptorPath;
    private Path shutdownBlockStorageScriptDescriptorPath;
    private Path initStorageScriptDescriptorPath;

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
        startupBlockStorageScriptDescriptorPath = recipePropertiesGenerator.loadResourceFromClasspath("classpath:velocity/startupBlockStorage.vm");
        createAttachBlockStorageScriptDescriptorPath = recipePropertiesGenerator.loadResourceFromClasspath("classpath:velocity/CreateAttachStorage.vm");
        formatMountBlockStorageScriptDescriptorPath = recipePropertiesGenerator.loadResourceFromClasspath("classpath:velocity/FormatMountStorage.vm");
        unmountDeleteBlockStorageSCriptDescriptorPath = recipePropertiesGenerator.loadResourceFromClasspath("classpath:velocity/UnmountDeleteStorage.vm");
        shutdownBlockStorageScriptDescriptorPath = recipePropertiesGenerator.loadResourceFromClasspath("classpath:velocity/shutdownBlockStorage.vm");
        initStorageScriptDescriptorPath = recipePropertiesGenerator.loadResourceFromClasspath("classpath:velocity/initStorage.vm");

        jsonMapper = new ObjectMapper();
    }

    public Path generateRecipe(final String deploymentName, final String topologyId, final Map<String, PaaSNodeTemplate> nodeTemplates,
            final List<PaaSNodeTemplate> roots, Map<String, ComputeTemplate> cloudResourcesMapping, Map<String, Network> networkMapping) throws IOException {
        // cleanup/create the topology recipe directory
        Path recipePath = cleanupDirectory(topologyId);
        List<String> serviceIds = Lists.newArrayList();
        if (roots == null || roots.isEmpty()) {
            throw new PaaSDeploymentException("No compute found in topology for deployment " + deploymentName);
        }
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
        paaSResourceMatcher.verifyNetworkNode(networkNode);
        Network network = networkMapping.get(networkNode.getId());
        if (network != null) {
            return network;
        }
        throw new ResourceMatchingFailedException("Failed to find a network for node <" + networkNode.getId() + ">");
    }

    private ComputeTemplate getComputeTemplateOrDie(Map<String, ComputeTemplate> cloudResourcesMapping, PaaSNodeTemplate node) {
        paaSResourceMatcher.verifyNode(node);
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
        cloudifyCommandGen.copyInternalResources(servicePath);

        // generate the properties file from the service node templates properties.
        recipePropertiesGenerator.generatePropertiesFile(context, computeNode);

        // copy artifacts for the nodes
        this.artifactCopier.copyAllArtifacts(context, computeNode);

        // check for blockStorage
        generateInitShutdownScripts(context, computeNode);

        // Generate startup life-cycle
        StartEvent startPlanStart = new BuildPlanGenerator().generate(computeNode);
        generateScript(startPlanStart, "start", context);

        // Generate stop and deletion life-cycle
        StartEvent stopPlanStart = new StopPlanGenerator().generate(computeNode);
        generateScript(stopPlanStart, "stop", context);

        // Generate custom commands
        addCustomCommands(computeNode, context);

        // generate global start detection script
        manageGlobalStartDetection(context);

        // generate global stop detection script
        manageGlobalStopDetection(context);

        // generate the service descriptor
        generateServiceDescriptor(context, serviceId, computeTemplate, networkName, computeNode.getScalingPolicy());
    }

    private void generateInitShutdownScripts(final RecipeGeneratorServiceContext context, final PaaSNodeTemplate computeNode) throws IOException {
        PaaSNodeTemplate blockStorageNode = computeNode.getAttachedNode();
        String initCommand = "{}";
        String shutdownCommand = "{}";
        if (blockStorageNode != null) {
            List<String> executions = Lists.newArrayList();

            // init
            generateInitStartUpStorageScripts(context, blockStorageNode, executions);
            // generate the final init script
            generateScriptWorkflow(context.getServicePath(), scriptDescriptorPath, INIT_LIFECYCLE, executions, null);

            executions.clear();

            // shutdown
            generateShutdownStorageScripts(context, blockStorageNode, executions);
            // generate the final shutdown script
            generateScriptWorkflow(context.getServicePath(), scriptDescriptorPath, SHUTDOWN_LIFECYCLE, executions, null);

            initCommand = "\"" + INIT_LIFECYCLE + ".groovy\"";
            shutdownCommand = "\"" + SHUTDOWN_LIFECYCLE + ".groovy\"";
        }
        context.getAdditionalProperties().put(INIT_COMMAND, initCommand);
        context.getAdditionalProperties().put(SHUTDOWN_COMMAND, shutdownCommand);
    }

    private void generateShutdownStorageScripts(final RecipeGeneratorServiceContext context, PaaSNodeTemplate blockStorageNode, List<String> shutdownExecutions)
            throws IOException {

        // generate shutdown BS

        String unmountDeleteCommand = getStorageUnmountDeleteCommand(context, blockStorageNode);
        Map<String, String> velocityProps = Maps.newHashMap();
        velocityProps.put("stoppedEvent", cloudifyCommandGen.getFireEventCommand(blockStorageNode.getId(), ToscaNodeLifecycleConstants.STOPPED));
        velocityProps.put(SHUTDOWN_COMMAND, unmountDeleteCommand);
        generateScriptWorkflow(context.getServicePath(), shutdownBlockStorageScriptDescriptorPath, STORAGE_SHUTDOWN_FILE_NAME, null, velocityProps);
        shutdownExecutions.add(cloudifyCommandGen.getGroovyCommand(STORAGE_SHUTDOWN_FILE_NAME.concat(".groovy"), null, null));
    }

    private String getStorageUnmountDeleteCommand(RecipeGeneratorServiceContext context, PaaSNodeTemplate blockStorageNode) throws IOException {

        Map<String, String> varParamsMap = Maps.newHashMap();
        varParamsMap.put("volumeId", "volumeId");
        varParamsMap.put("device", "device");

        // try getting a custom script routine
        String unmountDeleteCommand = getOperationCommandFromInterface(context, blockStorageNode, ToscaNodeLifecycleConstants.STANDARD,
                ToscaNodeLifecycleConstants.DELETE, false, varParamsMap, null);

        // if no custom management then generate the default routine
        if (StringUtils.isBlank(unmountDeleteCommand)) {

            Map<String, String> additionalProps = Maps.newHashMap();
            additionalProps.put("deletable",
                    String.valueOf(ToscaUtils.isFromType(AlienCustomTypes.DELETABLE_BLOCKSTORAGE_TYPE, blockStorageNode.getIndexedNodeType())));

            generateScriptWorkflow(context.getServicePath(), unmountDeleteBlockStorageSCriptDescriptorPath, DEFAULT_STORAGE_UNMOUNT_FILE_NAME, null,
                    additionalProps);

            unmountDeleteCommand = cloudifyCommandGen.getGroovyCommand(DEFAULT_STORAGE_UNMOUNT_FILE_NAME.concat(".groovy"), varParamsMap, null);
        }
        return unmountDeleteCommand;
    }

    private void generateInitStartUpStorageScripts(final RecipeGeneratorServiceContext context, PaaSNodeTemplate blockStorageNode, List<String> initExecutions)
    // FIXME try manage it via plan generator
            throws IOException {

        generateInitVolumeIdsScript(context, blockStorageNode, initExecutions);

        // startup (create, attach, format, mount)
        Map<String, String> velocityProps = Maps.newHashMap();
        // events
        // velocityProps.put("initial", cloudifyCommandGen.getFireEventCommand(blockStorageNode.getId(), PlanGeneratorConstants.STATE_INITIAL));
        velocityProps.put("createdEvent",
                cloudifyCommandGen.getFireBlockStorageEventCommand(blockStorageNode.getId(), ToscaNodeLifecycleConstants.CREATED, VOLUME_ID_VAR));
        velocityProps.put("configuredEvent", cloudifyCommandGen.getFireEventCommand(blockStorageNode.getId(), ToscaNodeLifecycleConstants.CONFIGURED));
        velocityProps.put("startedEvent", cloudifyCommandGen.getFireEventCommand(blockStorageNode.getId(), ToscaNodeLifecycleConstants.STARTED));
        velocityProps.put("availableEvent", cloudifyCommandGen.getFireEventCommand(blockStorageNode.getId(), ToscaNodeLifecycleConstants.AVAILABLE));

        String createAttachCommand = getStorageCreateAttachCommand(context, blockStorageNode);
        velocityProps.put(CREATE_COMMAND, createAttachCommand);

        String formatMountCommant = getStorageFormatMountCommand(context, blockStorageNode);
        velocityProps.put(CONFIGURE_COMMAND, formatMountCommant);

        // generate startup BS
        generateScriptWorkflow(context.getServicePath(), startupBlockStorageScriptDescriptorPath, STORAGE_STARTUP_FILE_NAME, null, velocityProps);
        initExecutions.add(cloudifyCommandGen.getGroovyCommand(STORAGE_STARTUP_FILE_NAME.concat(".groovy"), null, null));
    }

    private String getStorageFormatMountCommand(RecipeGeneratorServiceContext context, PaaSNodeTemplate blockStorageNode) throws IOException {
        Map<String, String> varParamsMap = MapUtil.newHashMap(new String[] { "device" }, new String[] { "device" });

        // try getting a custom script routine
        String formatMountCommand = getOperationCommandFromInterface(context, blockStorageNode, ToscaNodeLifecycleConstants.STANDARD,
                ToscaNodeLifecycleConstants.CONFIGURE, false, varParamsMap, null);

        // if no custom management then generate the default routine
        if (StringUtils.isBlank(formatMountCommand)) {

            // get the fs and the mounting location (path on the file system)
            Map<String, String> properties = blockStorageNode.getNodeTemplate().getProperties();
            String fs = DEFAULT_BLOCKSTORAGE_FS;
            String storageLocation = DEFAULT_BLOCKSTORAGE_LOCATION;
            if (properties != null) {
                fs = StringUtils.isNotBlank(properties.get(FS)) ? properties.get(FS) : fs;
                storageLocation = StringUtils.isNotBlank(properties.get(NormativeBlockStorageConstants.LOCATION)) ? properties
                        .get(NormativeBlockStorageConstants.LOCATION) : storageLocation;
            }
            Map<String, String> stringParamsMap = Maps.newHashMap();
            stringParamsMap.put(FS, fs);
            stringParamsMap.put(NormativeBlockStorageConstants.LOCATION, storageLocation);

            generateScriptWorkflow(context.getServicePath(), formatMountBlockStorageScriptDescriptorPath, DEFAULT_STORAGE_MOUNT_FILE_NAME, null, null);
            formatMountCommand = cloudifyCommandGen.getGroovyCommand(DEFAULT_STORAGE_MOUNT_FILE_NAME.concat(".groovy"), varParamsMap, stringParamsMap);
        }
        return formatMountCommand;
    }

    private String getStorageCreateAttachCommand(final RecipeGeneratorServiceContext context, PaaSNodeTemplate blockStorageNode) throws IOException {
        Map<String, String> varParamsMap = MapUtil.newHashMap(new String[] { "volumeId", "storageTemplate" }, new String[] {
                CONTEXT_THIS_INSTANCE_ATTRIBUTES + ".volumeId", CONTEXT_THIS_SERVICE_ATTRIBUTES + ".storageTemplateId" });

        String createAttachCommand = getOperationCommandFromInterface(context, blockStorageNode, ToscaNodeLifecycleConstants.STANDARD,
                ToscaNodeLifecycleConstants.CREATE, false, varParamsMap, null);

        // if no custom management then generate the default routine
        if (StringUtils.isBlank(createAttachCommand)) {
            Map<String, String> properties = blockStorageNode.getNodeTemplate().getProperties();
            String device = DEFAULT_BLOCKSTORAGE_DEVICE;
            if (properties != null && StringUtils.isNotBlank(properties.get(NormativeBlockStorageConstants.DEVICE))) {
                device = properties.get(NormativeBlockStorageConstants.DEVICE);
            }
            generateScriptWorkflow(context.getServicePath(), createAttachBlockStorageScriptDescriptorPath, DEFAULT_STORAGE_CREATE_FILE_NAME, null, null);
            createAttachCommand = cloudifyCommandGen.getGroovyCommand(DEFAULT_STORAGE_CREATE_FILE_NAME.concat(".groovy"), varParamsMap,
                    MapUtil.newHashMap(new String[] { NormativeBlockStorageConstants.DEVICE }, new String[] { device }));
        }
        return createAttachCommand;
    }

    private void generateInitVolumeIdsScript(RecipeGeneratorServiceContext context, PaaSNodeTemplate blockStorageNode, List<String> executions)
            throws IOException {
        Map<String, String> velocityProps = Maps.newHashMap();
        Map<String, String> properties = blockStorageNode.getNodeTemplate().getProperties();
        String size = null;
        String volumeIds = null;
        if (properties != null) {
            size = properties.get(NormativeBlockStorageConstants.SIZE);
            volumeIds = properties.get(NormativeBlockStorageConstants.VOLUME_ID);
            verifyNoVolumeIdForDeletableStorage(blockStorageNode, volumeIds);
        }

        // setting the storage template ID to be used when creating new volume for this application
        String storageTemplate = StringUtils.isNotBlank(size) ? storageTemplateMatcher.getTemplate(blockStorageNode) : storageTemplateMatcher
                .getDefaultTemplate();
        velocityProps.put("storageTemplateId", storageTemplate);

        // setting the volumes Ids array for instances
        String volumeIdsAsArrayString = "null";
        if (StringUtils.isNotBlank(volumeIds)) {
            String[] volumesIdsArray = volumeIds.split(",");
            volumeIdsAsArrayString = jsonMapper.writeValueAsString(volumesIdsArray);
        }
        velocityProps.put("instancesVolumeIds", volumeIdsAsArrayString);

        generateScriptWorkflow(context.getServicePath(), initStorageScriptDescriptorPath, INIT_STORAGE_SCRIPT_FILE_NAME, null, velocityProps);
        executions.add(cloudifyCommandGen.getGroovyCommand(INIT_STORAGE_SCRIPT_FILE_NAME.concat(".groovy"), null, null));
    }

    private void verifyNoVolumeIdForDeletableStorage(PaaSNodeTemplate blockStorageNode, String volumeIds) {
        if (ToscaUtils.isFromType(AlienCustomTypes.DELETABLE_BLOCKSTORAGE_TYPE, blockStorageNode.getIndexedNodeType()) && StringUtils.isNotBlank(volumeIds)) {
            throw new PaaSDeploymentException("Failed to generate scripts for BlockStorage <" + blockStorageNode.getId() + " >. A storage of type <"
                    + AlienCustomTypes.DELETABLE_BLOCKSTORAGE_TYPE + "> should not be provided with volumeIds.");
        }
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
            final Map<String, String> stepCommandsMap, final String commandsLogicalOperator, final Path velocityDescriptorPath) throws IOException {

        if (MapUtils.isNotEmpty(stepCommandsMap)) {

            // get a formated command for all commands found: command1 && command2 && command3 or command1 || command2 || command3
            // this assumes that every command should return a boolean
            String detectioncommand = cloudifyCommandGen.getMultipleGroovyCommand(commandsLogicalOperator, stepCommandsMap.values().toArray(new String[0]));
            generateScriptWorkflow(context.getServicePath(), velocityDescriptorPath, stepName, null,
                    MapUtil.newHashMap(new String[] { SERVICE_DETECTION_COMMAND, "is" + stepName }, new Object[] { detectioncommand, true }));

            String detectionFilePath = stepName + ".groovy";
            String groovyCommandForClosure = cloudifyCommandGen.getClosureGroovyCommand(detectionFilePath, null, null);
            String globalDectctionClosure = cloudifyCommandGen.getReturnGroovyCommand(groovyCommandForClosure);
            context.getAdditionalProperties().put(stepCommandName, globalDectctionClosure);
        }

    }

    private void addCustomCommands(final PaaSNodeTemplate nodeTemplate, final RecipeGeneratorServiceContext context) throws IOException {
        Interface customInterface = nodeTemplate.getIndexedNodeType().getInterfaces().get(NODE_CUSTOM_INTERFACE_NAME);
        if (customInterface != null) {
            // add the custom commands for each operations
            Map<String, Operation> operations = customInterface.getOperations();
            for (Entry<String, Operation> entry : operations.entrySet()) {
                String relativePath = CloudifyPaaSUtils.getNodeTypeRelativePath(nodeTemplate.getIndexedNodeType());

                // copy the implementation artifact of the custom command
                artifactCopier.copyImplementationArtifact(context, nodeTemplate.getCsarPath(), relativePath, entry.getValue().getImplementationArtifact());
                String key = CloudifyPaaSUtils.prefixWithTemplateId(entry.getKey(), nodeTemplate.getId());
                String artifactRef = relativePath + "/" + entry.getValue().getImplementationArtifact().getArtifactRef();
                String artifactType = entry.getValue().getImplementationArtifact().getArtifactType();

                // process the inputs parameters of the custom command
                Map<String, String> stringEvalResults = Maps.newHashMap();
                Map<String, String> runtimeEvalResults = Maps.newHashMap();
                funtionProcessor.processParameters(entry.getValue().getInputParameters(), stringEvalResults, runtimeEvalResults, nodeTemplate,
                        context.getTopologyNodeTemplates());

                // add the reserved env params
                runtimeEvalResults.put(RESEVED_ENV_KEYWORD, "args");

                // add base env vars
                addNodeBaseEnvVars(nodeTemplate, stringEvalResults, SELF, HOST, PARENT);

                String command;
                if (GROOVY_ARTIFACT_TYPE.equals(artifactType)) {
                    command = cloudifyCommandGen.getClosureGroovyCommand(artifactRef, runtimeEvalResults, stringEvalResults);
                } else {
                    // TODO handle SHELL_ARTIFACT_TYPE for custom commands
                    throw new PaaSDeploymentException("Operation <" + nodeTemplate.getId() + "." + NODE_CUSTOM_INTERFACE_NAME + "." + entry.getKey()
                            + "> is defined using an unsupported artifact type <" + artifactType + ">.");
                }

                if (log.isDebugEnabled()) {
                    log.debug("Configure customCommand " + key + " with value " + command);
                }
                context.getCustomCommands().put(key, command);
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

    private void generateScript(final StartEvent startEvent, final String lifecycleName, final RecipeGeneratorServiceContext context) throws IOException {
        List<String> executions = Lists.newArrayList();

        WorkflowStep currentStep = startEvent.getNextStep();
        while (currentStep != null && !(currentStep instanceof StopEvent)) {
            processWorkflowStep(context, currentStep, executions);
            currentStep = currentStep.getNextStep();
        }
        if (lifecycleName.equals("stop")) {
            executions.add(CloudifyCommandGenerator.SHUTDOWN_COMMAND);
            executions.add(CloudifyCommandGenerator.DESTROY_COMMAND);
        }
        if (lifecycleName.equals("start")) {
            executions.add(CloudifyCommandGenerator.DESTROY_COMMAND);
        }
        generateScriptWorkflow(context.getServicePath(), scriptDescriptorPath, lifecycleName, executions, null);
    }

    private void processWorkflowStep(final RecipeGeneratorServiceContext context, final WorkflowStep workflowStep, final List<String> executions)
            throws IOException {
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
                String command = cloudifyCommandGen.getWaitEventCommand(cfyServiceNameFromNodeTemplateOrDie(context.getNodeTemplateById(nodeStates.getKey())),
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

    private void processOperationCallActivity(final RecipeGeneratorServiceContext context, final OperationCallActivity operationCall,
            final List<String> executions) throws IOException {
        if (operationCall.getImplementationArtifact() == null) {
            return;
        }

        PaaSNodeTemplate paaSNodeTemplate = context.getNodeTemplateById(operationCall.getNodeTemplateId());
        if (operationCall.getRelationshipId() == null) {
            boolean isStartOperation = ToscaNodeLifecycleConstants.STANDARD.equals(operationCall.getInterfaceName())
                    && ToscaNodeLifecycleConstants.START.equals(operationCall.getOperationName());
            if (isStartOperation) {
                // if there is a stop detection script for this node and the operation is start, then we should inject a stop detection here.
                generateNodeDetectionCommand(context, paaSNodeTemplate, CLOUDIFY_EXTENSIONS_STOP_DETECTION_OPERATION_NAME, context.getStopDetectionCommands(),
                        true);
                // same for the start detection
                generateNodeDetectionCommand(context, paaSNodeTemplate, CLOUDIFY_EXTENSIONS_START_DETECTION_OPERATION_NAME,
                        context.getStartDetectionCommands(), true);
                // now generate the operation start itself
                generateNodeOperationCall(context, operationCall, executions, paaSNodeTemplate, isStartOperation);
                // add the startDetection command to the executions
                addLoppedCommandToExecutions(context.getStartDetectionCommands().get(operationCall.getNodeTemplateId()), executions);
                // we also manage the locator if one is define for this node
                manageProcessLocator(context, paaSNodeTemplate);
            } else {
                generateNodeOperationCall(context, operationCall, executions, paaSNodeTemplate, isStartOperation);
            }
        } else {
            generateRelationshipOperationCall(context, operationCall, executions, paaSNodeTemplate);
        }
    }

    private void addLoppedCommandToExecutions(final String command, final List<String> executions) {
        if (executions != null && StringUtils.isNotBlank(command)) {
            // here, we should add a looped ( "while" wrapped) command to the executions of the node template
            executions.add(cloudifyCommandGen.getLoopedGroovyCommand(command, null));
        }
    }

    private void manageProcessLocator(final RecipeGeneratorServiceContext context, final PaaSNodeTemplate paaSNodeTemplate) throws IOException {
        String command = getOperationCommandFromInterface(context, paaSNodeTemplate, NODE_CLOUDIFY_EXTENSIONS_INTERFACE_NAME,
                CLOUDIFY_EXTENSIONS_LOCATOR_OPERATION_NAME, true, null, null);
        if (command != null) {
            context.getProcessLocatorsCommands().put(paaSNodeTemplate.getId(), command);
        }

    }

    private void generateNodeDetectionCommand(final RecipeGeneratorServiceContext context, final PaaSNodeTemplate paaSNodeTemplate, final String operationName,
            final Map<String, String> commandsMap, final boolean closureCommand) throws IOException {
        String command = getOperationCommandFromInterface(context, paaSNodeTemplate, NODE_CLOUDIFY_EXTENSIONS_INTERFACE_NAME, operationName, closureCommand,
                null, null);
        if (command != null) {
            // here we register the command itself.
            commandsMap.put(paaSNodeTemplate.getId(), command);
        }
    }

    private String getOperationCommandFromInterface(final RecipeGeneratorServiceContext context, final PaaSNodeTemplate nodeTemplate,
            final String interfaceName, final String operationName, final boolean closureCommand, final Map<String, String> paramsAsVar,
            Map<String, String> stringParams) throws IOException {
        String command = null;
        Interface extensionsInterface = nodeTemplate.getIndexedNodeType().getInterfaces().get(interfaceName);
        if (extensionsInterface != null) {
            Operation operation = extensionsInterface.getOperations().get(operationName);
            if (operation != null) {
                String relativePath = CloudifyPaaSUtils.getNodeTypeRelativePath(nodeTemplate.getIndexedNodeType());
                stringParams = stringParams == null ? Maps.<String, String> newHashMap() : stringParams;
                addNodeBaseEnvVars(nodeTemplate, stringParams, SELF, PARENT, HOST);
                command = getCommandFromOperation(context, nodeTemplate, interfaceName, operationName, relativePath, operation.getImplementationArtifact(),
                        closureCommand, paramsAsVar, stringParams, operation.getInputParameters());
                if (StringUtils.isNotBlank(command)) {
                    this.artifactCopier.copyImplementationArtifact(context, nodeTemplate.getCsarPath(), relativePath, operation.getImplementationArtifact());
                }
            }
        }

        return command;
    }

    private void generateNodeOperationCall(final RecipeGeneratorServiceContext context, final OperationCallActivity operationCall,
            final List<String> executions, final PaaSNodeTemplate paaSNodeTemplate, final boolean isAsynchronous) throws IOException {
        String relativePath = CloudifyPaaSUtils.getNodeTypeRelativePath(paaSNodeTemplate.getIndexedNodeType());
        this.artifactCopier.copyImplementationArtifact(context, operationCall.getCsarPath(), relativePath, operationCall.getImplementationArtifact());
        // Map<String, String> copiedArtifactPath = context.getNodeArtifactsPaths().get(paaSNodeTemplate.getId());
        // String[] parameters = new String[] { CloudifyPaaSUtils.serviceIdFromNodeTemplateId(paaSNodeTemplate.getId()),
        // cfyServiceNameFromNodeTemplateOrDie(paaSNodeTemplate) };
        // parameters = addCopiedPathsToParams(copiedArtifactPath, parameters);
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
                operationCall.getImplementationArtifact(), false, varParamNames, stringParameters, operationCall.getInputParameters());

        if (isAsynchronous) {
            final String serviceId = CloudifyPaaSUtils.serviceIdFromNodeTemplateId(operationCall.getNodeTemplateId());
            String scriptName = "async-" + serviceId + "-" + operationCall.getOperationName() + "-" + UUID.randomUUID().toString();
            generateScriptWorkflow(context.getServicePath(), scriptDescriptorPath, scriptName, Lists.newArrayList(command), null);

            String asyncCommand = cloudifyCommandGen.getAsyncCommand(Lists.newArrayList(scriptName + ".groovy"), null);
            // if we are in the start lifecycle and there is either a startDetection or a stopDetection, then generate a conditional snippet.
            // so that we should only start the node if in restart case and the node is down (startDetetions(stopDetection) failure(success)), or if we are not
            // in the restart case
            if (operationCall.getOperationName().equals(ToscaNodeLifecycleConstants.START)) {
                String restartCondition = getRestartCondition(context, operationCall);
                String contextInstanceAttrRestart = CONTEXT_THIS_INSTANCE_ATTRIBUTES + ".restart";
                if (restartCondition != null) {
                    String trigger = contextInstanceAttrRestart + " != true || (" + restartCondition + ")";
                    // TODO: fire already started state instead
                    String alreadyStartedCommand = cloudifyCommandGen.getFireEventCommand(operationCall.getNodeTemplateId(),
                            ToscaNodeLifecycleConstants.STARTED);
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

    private String getCommandFromOperation(final RecipeGeneratorServiceContext context, final IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate,
            final String interfaceName, final String operationName, final String relativePath, final ImplementationArtifact artifact,
            final boolean closureCommand, final Map<String, String> varEnvVars, final Map<String, String> stringEnvVars,
            Map<String, IOperationParameter> inputParameters) throws IOException {
        if (artifact == null || StringUtils.isBlank(artifact.getArtifactRef())) {
            return null;
        }

        Map<String, String> runtimeEvalResults = Maps.newHashMap();
        Map<String, String> stringEvalResults = Maps.newHashMap();
        funtionProcessor.processParameters(inputParameters, stringEvalResults, runtimeEvalResults, basePaaSTemplate, context.getTopologyNodeTemplates());
        stringEvalResults = alien4cloud.utils.CollectionUtils.merge(stringEnvVars, stringEvalResults, false);
        runtimeEvalResults = alien4cloud.utils.CollectionUtils.merge(varEnvVars, runtimeEvalResults, false);

        String scriptPath = relativePath + "/" + artifact.getArtifactRef();
        String command;
        if (GROOVY_ARTIFACT_TYPE.equals(artifact.getArtifactType())) {
            command = closureCommand ? cloudifyCommandGen.getClosureGroovyCommand(scriptPath, runtimeEvalResults, stringEvalResults) : cloudifyCommandGen
                    .getGroovyCommand(scriptPath, runtimeEvalResults, stringEvalResults);
        } else if (SHELL_ARTIFACT_TYPE.equals(artifact.getArtifactType())) {
            // TODO pass params to the shell scripts
            command = cloudifyCommandGen.getBashCommand(scriptPath, runtimeEvalResults, stringEvalResults);
        } else {
            throw new PaaSDeploymentException("Operation <" + basePaaSTemplate.getId() + "." + interfaceName + "." + operationName
                    + "> is defined using an unsupported artifact type <" + artifact.getArtifactType() + ">.");
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

    private void generateScriptWorkflow(final Path servicePath, final Path velocityDescriptorPath, final String lifecycle, final List<String> executions,
            final Map<String, ? extends Object> additionalPropeties) throws IOException {
        Path outputPath = servicePath.resolve(lifecycle + ".groovy");

        Map<String, Object> properties = Maps.newHashMap();
        properties.put(SCRIPT_LIFECYCLE, lifecycle);
        properties.put(SCRIPTS, executions);
        properties = CollectionUtils.merge(additionalPropeties, properties, false);
        VelocityUtil.writeToOutputFile(velocityDescriptorPath, outputPath, properties);
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