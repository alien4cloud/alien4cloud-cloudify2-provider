package alien4cloud.paas.cloudify2.generator;

import static alien4cloud.paas.cloudify2.generator.RecipeGeneratorConstants.CONTEXT_THIS_INSTANCE_ATTRIBUTES;
import static alien4cloud.paas.cloudify2.generator.RecipeGeneratorConstants.CONTEXT_THIS_SERVICE_ATTRIBUTES;
import static alien4cloud.paas.cloudify2.generator.RecipeGeneratorConstants.SHUTDOWN_COMMAND;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import alien4cloud.paas.exception.PaaSDeploymentException;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.tosca.ToscaUtils;
import alien4cloud.tosca.normative.AlienCustomTypes;
import alien4cloud.tosca.normative.NormativeBlockStorageConstants;
import alien4cloud.utils.MapUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Maps;

@Component
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class StorageScriptGenerator extends AbstractCloudifyScriptGenerator {
    private static final String INIT_STORAGE_SCRIPT_FILE_NAME = "initStorage";
    private static final String VOLUME_ID_KEY = "volumeId";
    private static final String DEVICE_KEY = "device";
    private static final String FS_KEY = "file_system";
    private static final String LOCATION_KEY = "location";
    private static final String STORAGE_TEMPLATE_KEY = "storageTemplate";
    private static final String DEFAULT_BLOCKSTORAGE_FS = "ext4";
    private static final String DEFAULT_BLOCKSTORAGE_LOCATION = "/mountedStorage";
    private static final String DEFAULT_BLOCKSTORAGE_DEVICE = "/dev/vdb";

    @Resource
    private CommandGenerator commandGenerator;

    private String STORAGE_STARTUP_FILE_NAME = "startupBlockStorage";
    private ObjectMapper jsonMapper;
    private Path startupBlockStorageScriptDescriptorPath;
    private Path initStorageScriptDescriptorPath;
    private String DEFAULT_STORAGE_CREATE_FILE_NAME = "createAttachStorage";
    private Path createAttachBlockStorageScriptDescriptorPath;
    private String DEFAULT_STORAGE_MOUNT_FILE_NAME = "formatMountStorage";
    private Path formatMountBlockStorageScriptDescriptorPath;
    private String DEFAULT_STORAGE_UNMOUNT_FILE_NAME = "unmountDeleteStorage";
    private String STORAGE_SHUTDOWN_FILE_NAME = "shutdownBlockStorage";
    private Path unmountDeleteBlockStorageSCriptDescriptorPath;
    private Path shutdownBlockStorageScriptDescriptorPath;

    @PostConstruct
    public void initialize() throws IOException {
        startupBlockStorageScriptDescriptorPath = loadResourceFromClasspath("classpath:velocity/startupBlockStorage.vm");
        initStorageScriptDescriptorPath = loadResourceFromClasspath("classpath:velocity/initStorage.vm");
        createAttachBlockStorageScriptDescriptorPath = loadResourceFromClasspath("classpath:velocity/CreateAttachStorage.vm");
        formatMountBlockStorageScriptDescriptorPath = loadResourceFromClasspath("classpath:velocity/FormatMountStorage.vm");
        unmountDeleteBlockStorageSCriptDescriptorPath = loadResourceFromClasspath("classpath:velocity/UnmountDeleteStorage.vm");
        shutdownBlockStorageScriptDescriptorPath = loadResourceFromClasspath("classpath:velocity/shutdownBlockStorage.vm");
        jsonMapper = new ObjectMapper();
    }

    public void generateInitStartUpStorageScripts(final RecipeGeneratorServiceContext context, PaaSNodeTemplate blockStorageNode, String storageName,
            List<String> executions)
    // FIXME try manage it via plan generator
            throws IOException {
        // do nothing if no blockstorage
        if (blockStorageNode == null) {
            return;
        }

        generateInitVolumeIdsScript(context, blockStorageNode, storageName, executions);
        generateStartUpStorageScript(context, blockStorageNode, executions);
    }

    public void generateShutdownStorageScript(final RecipeGeneratorServiceContext context, PaaSNodeTemplate blockStorageNode, List<String> executions)
            throws IOException {
        // do nothing if no blockstorage
        if (blockStorageNode == null) {
            return;
        }

        String unmountDeleteCommand = getStorageUnmountDeleteCommand(context, blockStorageNode);
        Map<String, String> velocityProps = Maps.newHashMap();
        velocityProps.put("stoppedEvent",
                commandGenerator.getFireEventCommand(blockStorageNode.getId(), ToscaNodeLifecycleConstants.STOPPED, context.getEventsLeaseInHour()));
        velocityProps.put(SHUTDOWN_COMMAND, unmountDeleteCommand);
        generateScriptWorkflow(context.getServicePath(), shutdownBlockStorageScriptDescriptorPath, STORAGE_SHUTDOWN_FILE_NAME, null, velocityProps);
        executions.add(commandGenerator.getGroovyCommand(STORAGE_SHUTDOWN_FILE_NAME.concat(".groovy"), null, null));
    }

    private void generateInitVolumeIdsScript(RecipeGeneratorServiceContext context, PaaSNodeTemplate blockStorageNode, String storageName,
            List<String> executions) throws IOException {
        Map<String, String> velocityProps = Maps.newHashMap();
        // setting the storage template ID to be used when creating new volume for this application
        velocityProps.put(STORAGE_TEMPLATE_KEY, storageName);

        Map<String, String> properties = blockStorageNode.getNodeTemplate().getProperties();
        String volumeIds = null;
        if (properties != null) {
            volumeIds = properties.get(NormativeBlockStorageConstants.VOLUME_ID);
            verifyNoVolumeIdForDeletableStorage(blockStorageNode, volumeIds);
        }

        // setting the volumes Ids array for instances
        String volumeIdsAsArrayString = "null";
        if (StringUtils.isNotBlank(volumeIds)) {
            String[] volumesIdsArray = volumeIds.split(",");
            volumeIdsAsArrayString = jsonMapper.writeValueAsString(volumesIdsArray);
        }
        velocityProps.put("instancesVolumeIds", volumeIdsAsArrayString);

        generateScriptWorkflow(context.getServicePath(), initStorageScriptDescriptorPath, INIT_STORAGE_SCRIPT_FILE_NAME, null, velocityProps);
        executions.add(commandGenerator.getGroovyCommand(INIT_STORAGE_SCRIPT_FILE_NAME.concat(".groovy"), null, null));
    }

    private void generateStartUpStorageScript(final RecipeGeneratorServiceContext context, PaaSNodeTemplate blockStorageNode, List<String> executions)
            throws IOException {
        // startup (create, attach, format, mount)
        Map<String, String> velocityProps = Maps.newHashMap();
        // events
        Double lease = context.getEventsLeaseInHour();
        velocityProps.put("createdEvent",
                commandGenerator.getFireBlockStorageEventCommand(blockStorageNode.getId(), ToscaNodeLifecycleConstants.CREATED, VOLUME_ID_KEY, lease));
        velocityProps.put("configuredEvent", commandGenerator.getFireEventCommand(blockStorageNode.getId(), ToscaNodeLifecycleConstants.CONFIGURED, lease));
        velocityProps.put("startedEvent", commandGenerator.getFireEventCommand(blockStorageNode.getId(), ToscaNodeLifecycleConstants.STARTED, lease));
        velocityProps.put("availableEvent", commandGenerator.getFireEventCommand(blockStorageNode.getId(), ToscaNodeLifecycleConstants.AVAILABLE, lease));

        String createAttachCommand = getStorageCreateAttachCommand(context, blockStorageNode);
        velocityProps.put(RecipeGeneratorConstants.CREATE_COMMAND, createAttachCommand);

        String formatMountCommant = getStorageFormatMountCommand(context, blockStorageNode);
        velocityProps.put(RecipeGeneratorConstants.CONFIGURE_COMMAND, formatMountCommant);

        // generate startup BS
        generateScriptWorkflow(context.getServicePath(), startupBlockStorageScriptDescriptorPath, STORAGE_STARTUP_FILE_NAME, null, velocityProps);
        executions.add(commandGenerator.getGroovyCommand(STORAGE_STARTUP_FILE_NAME.concat(".groovy"), null, null));
    }

    private String getStorageFormatMountCommand(RecipeGeneratorServiceContext context, PaaSNodeTemplate blockStorageNode) throws IOException {
        ExecEnvMaps envMaps = new ExecEnvMaps();
        envMaps.runtimes.put(DEVICE_KEY, DEVICE_KEY);
        // try getting a custom script routine
        String formatMountCommand = getOperationCommandFromInterface(context, blockStorageNode, ToscaNodeLifecycleConstants.STANDARD,
                ToscaNodeLifecycleConstants.CONFIGURE, envMaps);

        // if no custom management then generate the default routine
        if (StringUtils.isBlank(formatMountCommand)) {

            // get the fs and the mounting location (path on the file system)
            Map<String, String> properties = blockStorageNode.getNodeTemplate().getProperties();
            String fs = DEFAULT_BLOCKSTORAGE_FS;
            String storageLocation = DEFAULT_BLOCKSTORAGE_LOCATION;
            if (properties != null) {
                fs = StringUtils.isNotBlank(properties.get(FS_KEY)) ? properties.get(FS_KEY) : fs;
                storageLocation = StringUtils.isNotBlank(properties.get(NormativeBlockStorageConstants.LOCATION)) ? properties
                        .get(NormativeBlockStorageConstants.LOCATION) : storageLocation;
            }
            envMaps.strings.put(FS_KEY, fs);
            envMaps.strings.put(LOCATION_KEY, storageLocation);

            generateScriptWorkflow(context.getServicePath(), formatMountBlockStorageScriptDescriptorPath, DEFAULT_STORAGE_MOUNT_FILE_NAME, null, null);
            formatMountCommand = commandGenerator.getGroovyCommand(DEFAULT_STORAGE_MOUNT_FILE_NAME.concat(".groovy"), envMaps.runtimes, envMaps.strings);
        }
        return formatMountCommand;
    }

    private String getStorageCreateAttachCommand(final RecipeGeneratorServiceContext context, PaaSNodeTemplate blockStorageNode) throws IOException {
        ExecEnvMaps envMaps = new ExecEnvMaps();
        envMaps.runtimes.put(VOLUME_ID_KEY, CONTEXT_THIS_INSTANCE_ATTRIBUTES + "." + VOLUME_ID_KEY);
        envMaps.runtimes.put(STORAGE_TEMPLATE_KEY, CONTEXT_THIS_SERVICE_ATTRIBUTES + "." + STORAGE_TEMPLATE_KEY);

        String createAttachCommand = getOperationCommandFromInterface(context, blockStorageNode, ToscaNodeLifecycleConstants.STANDARD,
                ToscaNodeLifecycleConstants.CREATE, envMaps);

        // if no custom management then generate the default routine
        if (StringUtils.isBlank(createAttachCommand)) {
            Map<String, String> properties = blockStorageNode.getNodeTemplate().getProperties();
            String device = DEFAULT_BLOCKSTORAGE_DEVICE;
            if (properties != null && StringUtils.isNotBlank(properties.get(NormativeBlockStorageConstants.DEVICE))) {
                device = properties.get(NormativeBlockStorageConstants.DEVICE);
            }
            generateScriptWorkflow(context.getServicePath(), createAttachBlockStorageScriptDescriptorPath, DEFAULT_STORAGE_CREATE_FILE_NAME, null, null);
            createAttachCommand = commandGenerator.getGroovyCommand(DEFAULT_STORAGE_CREATE_FILE_NAME.concat(".groovy"), envMaps.runtimes,
                    MapUtil.newHashMap(new String[] { DEVICE_KEY }, new String[] { device }));
        }
        return createAttachCommand;
    }

    private String getStorageUnmountDeleteCommand(RecipeGeneratorServiceContext context, PaaSNodeTemplate blockStorageNode) throws IOException {

        ExecEnvMaps envMaps = new ExecEnvMaps();
        envMaps.runtimes.put(VOLUME_ID_KEY, VOLUME_ID_KEY);
        envMaps.runtimes.put(DEVICE_KEY, DEVICE_KEY);

        // try getting a custom script routine
        String unmountDeleteCommand = getOperationCommandFromInterface(context, blockStorageNode, ToscaNodeLifecycleConstants.STANDARD,
                ToscaNodeLifecycleConstants.DELETE, envMaps);

        // if no custom management then generate the default routine
        if (StringUtils.isBlank(unmountDeleteCommand)) {

            Map<String, String> additionalProps = Maps.newHashMap();
            additionalProps.put("deletable",
                    String.valueOf(ToscaUtils.isFromType(AlienCustomTypes.DELETABLE_BLOCKSTORAGE_TYPE, blockStorageNode.getIndexedToscaElement())));

            generateScriptWorkflow(context.getServicePath(), unmountDeleteBlockStorageSCriptDescriptorPath, DEFAULT_STORAGE_UNMOUNT_FILE_NAME, null,
                    additionalProps);

            unmountDeleteCommand = commandGenerator.getGroovyCommand(DEFAULT_STORAGE_UNMOUNT_FILE_NAME.concat(".groovy"), envMaps.runtimes, null);
        }
        return unmountDeleteCommand;
    }

    private void verifyNoVolumeIdForDeletableStorage(PaaSNodeTemplate blockStorageNode, String volumeIds) {
        if (ToscaUtils.isFromType(AlienCustomTypes.DELETABLE_BLOCKSTORAGE_TYPE, blockStorageNode.getIndexedToscaElement()) && StringUtils.isNotBlank(volumeIds)) {
            throw new PaaSDeploymentException("Failed to generate scripts for BlockStorage <" + blockStorageNode.getId() + " >. A storage of type <"
                    + AlienCustomTypes.DELETABLE_BLOCKSTORAGE_TYPE + "> should not be provided with volumeIds.");
        }
    }
}
