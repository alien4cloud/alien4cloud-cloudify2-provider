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

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import alien4cloud.common.AlienConstants;
import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.paas.cloudify2.utils.CloudifyPaaSUtils;
import alien4cloud.paas.exception.PaaSDeploymentException;
import alien4cloud.paas.function.FunctionEvaluator;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.tosca.normative.NormativeBlockStorageConstants;
import alien4cloud.utils.AlienUtils;
import alien4cloud.utils.MapUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

@Component
@Slf4j
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class StorageScriptGenerator extends AbstractCloudifyScriptGenerator {
    private static final String STORAGE_ID_KEY = "storageId";
    private static final String INIT_STORAGE_SCRIPT_FILE_NAME = "initStorage";
    private static final String VOLUME_ID_KEY = "volumeId";
    private static final String DEVICE_KEY = "device";
    private static final String FS_KEY = "file_system";
    private static final String LOCATION_KEY = "location";
    private static final String STORAGE_TEMPLATE_KEY = "storageTemplate";
    private static final String DEFAULT_BLOCKSTORAGE_FS = "ext4";
    private static final String DEFAULT_BLOCKSTORAGE_LOCATION = "/mountedStorage";
    private static final String DEFAULT_BLOCKSTORAGE_DEVICE = "/dev/vdb";
    private static final String STORAGE_SCRIPTS_DIR = "storagesScripts";

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
        startupBlockStorageScriptDescriptorPath = commandGenerator.loadResourceFromClasspath("classpath:velocity/startupBlockStorage.vm");
        initStorageScriptDescriptorPath = commandGenerator.loadResourceFromClasspath("classpath:velocity/initStorage.vm");
        createAttachBlockStorageScriptDescriptorPath = commandGenerator.loadResourceFromClasspath("classpath:velocity/CreateAttachStorage.vm");
        formatMountBlockStorageScriptDescriptorPath = commandGenerator.loadResourceFromClasspath("classpath:velocity/FormatMountStorage.vm");
        unmountDeleteBlockStorageSCriptDescriptorPath = commandGenerator.loadResourceFromClasspath("classpath:velocity/UnmountDeleteStorage.vm");
        shutdownBlockStorageScriptDescriptorPath = commandGenerator.loadResourceFromClasspath("classpath:velocity/shutdownBlockStorage.vm");
        jsonMapper = new ObjectMapper();
    }

    public void generateInitStartUpStorageScripts(final RecipeGeneratorServiceContext context, List<PaaSNodeTemplate> storageNodes,
            Map<String, String> storageNames, String availabilityZone, List<String> executions) throws IOException {
        // FIXME try manage it via plan generator
        // do nothing if no blockstorage
        if (CollectionUtils.isEmpty(storageNodes)) {
            return;
        }
        List<String> initScripts = Lists.newArrayList();
        List<String> startUpScripts = Lists.newArrayList();
        boolean multiStorages = storageNodes.size() > 1;
        DeviceGenerator deviceGen = new DeviceGenerator();
        for (PaaSNodeTemplate storageNode : storageNodes) {
            generateInitVolumeIdsScript(context, storageNode, storageNames.get(storageNode.getId()), multiStorages, initScripts);

            String defaultLocation = !multiStorages ? DEFAULT_BLOCKSTORAGE_LOCATION : DEFAULT_BLOCKSTORAGE_LOCATION + "_"
                    + CloudifyPaaSUtils.serviceIdFromNodeTemplateId(storageNode.getId());
            generateStartUpStorageScript(context, storageNode, availabilityZone, startUpScripts, defaultLocation, deviceGen);
        }
        executions.add(commandGenerator.getParallelCommand(initScripts, null));
        executions.add(commandGenerator.getParallelCommand(startUpScripts, null));
    }

    public void generateShutdownStorageScript(final RecipeGeneratorServiceContext context, List<PaaSNodeTemplate> storageNodes, List<String> executions)
            throws IOException {
        // do nothing if no blockstorage
        if (CollectionUtils.isEmpty(storageNodes)) {
            return;
        }
        List<String> shutdownStorageScripts = Lists.newArrayList();

        // we generate scrips for every storage
        for (PaaSNodeTemplate storageNode : storageNodes) {
            String unmountDeleteCommand = getStorageUnmountDeleteCommand(context, storageNode);
            Map<String, String> velocityProps = Maps.newHashMap();
            velocityProps.put("stoppedEvent",
                    commandGenerator.getFireEventCommand(storageNode.getId(), ToscaNodeLifecycleConstants.STOPPED, context.getEventsLeaseInHour()));
            velocityProps.put(SHUTDOWN_COMMAND, unmountDeleteCommand);
            velocityProps.put(STORAGE_ID_KEY, storageNode.getId());
            String fileName = STORAGE_SCRIPTS_DIR + "/" + STORAGE_SHUTDOWN_FILE_NAME + "-" + CloudifyPaaSUtils.serviceIdFromNodeTemplateId(storageNode.getId());
            generateScriptWorkflow(context.getServicePath(), shutdownBlockStorageScriptDescriptorPath, fileName, null, velocityProps);
            shutdownStorageScripts.add(fileName.concat(".groovy"));
        }

        // we will run them in //
        executions.add(commandGenerator.getParallelCommand(shutdownStorageScripts, null));
    }

    private void generateInitVolumeIdsScript(RecipeGeneratorServiceContext context, PaaSNodeTemplate blockStorageNode, String storageName,
            boolean multiStorages, List<String> executions) throws IOException {
        Map<String, String> velocityProps = Maps.newHashMap();
        // setting the storage template ID to be used when creating new volume for this application
        velocityProps.put(STORAGE_TEMPLATE_KEY, storageName);
        velocityProps.put(STORAGE_ID_KEY, blockStorageNode.getId());

        Map<String, AbstractPropertyValue> properties = blockStorageNode.getNodeTemplate().getProperties();
        String volumeIds = null;
        if (properties != null) {
            AbstractPropertyValue volumeIdsValue = properties.get(NormativeBlockStorageConstants.VOLUME_ID);
            if (volumeIdsValue != null) {
                if (volumeIdsValue instanceof ScalarPropertyValue) {
                    volumeIds = ((ScalarPropertyValue) volumeIdsValue).getValue();
                } else {
                    log.warn(NormativeBlockStorageConstants.VOLUME_ID + " is not of type Scalar, it's not supported by the driver, volume will not be reused");
                }
            }
            verifyVolumeIdsForStorage(blockStorageNode.getId(), volumeIds, context.isDeleteStorages());
        }

        // setting the volumes Ids array for instances
        String volumeIdsAsArrayString = "null";
        if (StringUtils.isNotBlank(volumeIds)) {
            String[] volumesIdsArray = parseVolumesIds(volumeIds);
            volumeIdsAsArrayString = jsonMapper.writeValueAsString(volumesIdsArray);
        }
        velocityProps.put("instancesVolumeIds", volumeIdsAsArrayString);

        String fileName = STORAGE_SCRIPTS_DIR + "/" + INIT_STORAGE_SCRIPT_FILE_NAME + "-"
                + CloudifyPaaSUtils.serviceIdFromNodeTemplateId(blockStorageNode.getId());
        generateScriptWorkflow(context.getServicePath(), initStorageScriptDescriptorPath, fileName, null, velocityProps);
        executions.add(fileName.concat(".groovy"));
    }

    private String[] parseVolumesIds(String volumeIds) {
        String[] splitted = volumeIds.split(",");
        String[] toReturn = new String[splitted.length];
        int i = 0;
        for (String zoneAndVolumeId : splitted) {
            int index = zoneAndVolumeId.indexOf(AlienConstants.STORAGE_AZ_VOLUMEID_SEPARATOR);
            if (index > 0) {
                toReturn[i++] = zoneAndVolumeId.substring(index + 1, zoneAndVolumeId.length());
            } else {
                toReturn[i++] = zoneAndVolumeId;
            }
        }
        return toReturn;
    }

    private void generateStartUpStorageScript(final RecipeGeneratorServiceContext context, PaaSNodeTemplate storageNode, String availabilityZone,
            List<String> executions, String defaultLocation, DeviceGenerator deviceGenerator) throws IOException {
        // startup (create, attach, format, mount)
        Map<String, String> velocityProps = Maps.newHashMap();
        velocityProps.put(STORAGE_ID_KEY, storageNode.getId());
        // events
        Double lease = context.getEventsLeaseInHour();
        String volumeIdKey = StringUtils.isNotBlank(availabilityZone) ? "\"" + availabilityZone + AlienConstants.STORAGE_AZ_VOLUMEID_SEPARATOR + "\"+"
                + VOLUME_ID_KEY : VOLUME_ID_KEY;
        velocityProps.put("createdEvent",
                commandGenerator.getFireBlockStorageEventCommand(storageNode.getId(), ToscaNodeLifecycleConstants.CREATED, volumeIdKey, lease));
        velocityProps.put("configuredEvent", commandGenerator.getFireEventCommand(storageNode.getId(), ToscaNodeLifecycleConstants.CONFIGURED, lease));
        velocityProps.put("startedEvent", commandGenerator.getFireEventCommand(storageNode.getId(), ToscaNodeLifecycleConstants.STARTED, lease));
        velocityProps.put("availableEvent", commandGenerator.getFireEventCommand(storageNode.getId(), ToscaNodeLifecycleConstants.AVAILABLE, lease));

        // create and attach script
        String createAttachCommand = getStorageCreateAttachCommand(context, storageNode, deviceGenerator);
        velocityProps.put(RecipeGeneratorConstants.CREATE_COMMAND, createAttachCommand);

        // format and mount script
        String formatMountCommant = getStorageFormatMountCommand(context, storageNode, defaultLocation);
        velocityProps.put(RecipeGeneratorConstants.CONFIGURE_COMMAND, formatMountCommant);

        // generate startup BS
        String fileName = STORAGE_SCRIPTS_DIR + "/" + STORAGE_STARTUP_FILE_NAME + "-" + CloudifyPaaSUtils.serviceIdFromNodeTemplateId(storageNode.getId());
        generateScriptWorkflow(context.getServicePath(), startupBlockStorageScriptDescriptorPath, fileName, null, velocityProps);
        executions.add(fileName.concat(".groovy"));
    }

    private String getStorageFormatMountCommand(RecipeGeneratorServiceContext context, PaaSNodeTemplate storageNode, String defaultLocation) throws IOException {
        ExecEnvMaps envMaps = new ExecEnvMaps();
        envMaps.runtimes.put(DEVICE_KEY, DEVICE_KEY);
        // try getting a custom script routine
        String formatMountCommand = getOperationCommandFromInterface(context, storageNode, ToscaNodeLifecycleConstants.STANDARD,
                ToscaNodeLifecycleConstants.CONFIGURE, envMaps, null);

        // if no custom management then generate the default routine
        if (StringUtils.isBlank(formatMountCommand)) {

            // get the fs and the mounting location (path on the file system)
            Map<String, AbstractPropertyValue> properties = storageNode.getNodeTemplate().getProperties();
            String fs = FunctionEvaluator.getScalarValue(MapUtils.getObject(properties, FS_KEY));
            fs = StringUtils.isNotBlank(fs) ? fs : DEFAULT_BLOCKSTORAGE_FS;
            String storageLocation = FunctionEvaluator.getScalarValue(MapUtils.getObject(properties, NormativeBlockStorageConstants.LOCATION));
            storageLocation = StringUtils.isNotBlank(storageLocation) ? storageLocation : defaultLocation;
            envMaps.strings.put(FS_KEY, fs);
            envMaps.strings.put(LOCATION_KEY, storageLocation);

            String fileName = STORAGE_SCRIPTS_DIR + "/" + DEFAULT_STORAGE_MOUNT_FILE_NAME + "-"
                    + CloudifyPaaSUtils.serviceIdFromNodeTemplateId(storageNode.getId());

            Map<String, String> velocityProps = Maps.newHashMap();
            velocityProps.put(STORAGE_ID_KEY, storageNode.getId());
            generateScriptWorkflow(context.getServicePath(), formatMountBlockStorageScriptDescriptorPath, fileName, null, velocityProps);

            formatMountCommand = commandGenerator.getGroovyCommand(fileName.concat(".groovy"), envMaps.runtimes, envMaps.strings, null, null);
        }
        return formatMountCommand;
    }

    private String getStorageCreateAttachCommand(final RecipeGeneratorServiceContext context, PaaSNodeTemplate storageNode, DeviceGenerator deviceGenerator)
            throws IOException {
        ExecEnvMaps envMaps = new ExecEnvMaps();
        String nodeVolumeIdKey = AlienUtils.prefixWith(AlienConstants.ATTRIBUTES_NAME_SEPARATOR, VOLUME_ID_KEY, storageNode.getId());
        String nodeStorageKey = AlienUtils.prefixWith(AlienConstants.ATTRIBUTES_NAME_SEPARATOR, STORAGE_TEMPLATE_KEY, storageNode.getId());
        envMaps.runtimes.put(VOLUME_ID_KEY, CONTEXT_THIS_INSTANCE_ATTRIBUTES + "[\"" + nodeVolumeIdKey + "\"]");
        envMaps.runtimes.put(STORAGE_TEMPLATE_KEY, CONTEXT_THIS_SERVICE_ATTRIBUTES + "[\"" + nodeStorageKey + "\"]");

        String createAttachCommand = getOperationCommandFromInterface(context, storageNode, ToscaNodeLifecycleConstants.STANDARD,
                ToscaNodeLifecycleConstants.CREATE, envMaps, null);

        // if no custom management then generate the default routine
        if (StringUtils.isBlank(createAttachCommand)) {

            Map<String, AbstractPropertyValue> properties = storageNode.getNodeTemplate().getProperties();
            String device = FunctionEvaluator.getScalarValue(MapUtils.getObject(properties, NormativeBlockStorageConstants.DEVICE));
            device = StringUtils.isNotBlank(device) ? device : deviceGenerator.getNextAvailable();
            String fileName = STORAGE_SCRIPTS_DIR + "/" + DEFAULT_STORAGE_CREATE_FILE_NAME + "-"
                    + CloudifyPaaSUtils.serviceIdFromNodeTemplateId(storageNode.getId());

            Map<String, String> velocityProps = Maps.newHashMap();
            velocityProps.put(STORAGE_ID_KEY, storageNode.getId());
            generateScriptWorkflow(context.getServicePath(), createAttachBlockStorageScriptDescriptorPath, fileName, null, velocityProps);

            createAttachCommand = commandGenerator.getGroovyCommand(fileName.concat(".groovy"), envMaps.runtimes,
                    MapUtil.newHashMap(new String[] { DEVICE_KEY }, new String[] { device }), null, null);
        }
        return createAttachCommand;
    }

    private String getStorageUnmountDeleteCommand(RecipeGeneratorServiceContext context, PaaSNodeTemplate storageNode) throws IOException {

        ExecEnvMaps envMaps = new ExecEnvMaps();
        envMaps.runtimes.put(VOLUME_ID_KEY, VOLUME_ID_KEY);
        envMaps.runtimes.put(DEVICE_KEY, DEVICE_KEY);

        // try getting a custom script routine
        String unmountDeleteCommand = getOperationCommandFromInterface(context, storageNode, ToscaNodeLifecycleConstants.STANDARD,
                ToscaNodeLifecycleConstants.DELETE, envMaps, null);

        // if no custom management then generate the default routine
        if (StringUtils.isBlank(unmountDeleteCommand)) {

            String fileName = STORAGE_SCRIPTS_DIR + "/" + DEFAULT_STORAGE_UNMOUNT_FILE_NAME + "-"
                    + CloudifyPaaSUtils.serviceIdFromNodeTemplateId(storageNode.getId());

            // getting deletable BStorage from context (deployment properties)
            Map<String, String> velocityProps = Maps.newHashMap();
            velocityProps.put("deletable", Boolean.toString(context.isDeleteStorages()));
            velocityProps.put(STORAGE_ID_KEY, storageNode.getId());

            generateScriptWorkflow(context.getServicePath(), unmountDeleteBlockStorageSCriptDescriptorPath, fileName, null, velocityProps);

            unmountDeleteCommand = commandGenerator.getGroovyCommand(fileName.concat(".groovy"), envMaps.runtimes, null, null, null);
        }
        return unmountDeleteCommand;
    }

    private void verifyVolumeIdsForStorage(String blockStorageNodeId, String volumeIds, boolean isDeletableBlockStorage) {
        if (isDeletableBlockStorage && StringUtils.isNotBlank(volumeIds)) {
            throw new PaaSDeploymentException("Failed to generate scripts for BlockStorage <" + blockStorageNodeId
                    + " >. Since deletion of storage is activated,  it should not be provided with volumeIds.");
        }
    }

    private class DeviceGenerator {
        private String lastGenerated;

        public String getNextAvailable() {
            lastGenerated = getNext(lastGenerated);
            return lastGenerated;
        }

        private String getNext(String currentDevice) {
            if (StringUtils.isBlank(currentDevice)) {
                return DEFAULT_BLOCKSTORAGE_DEVICE;
            }
            String firstChars = currentDevice.substring(0, currentDevice.length() - 1);
            char lastChar = lastGenerated.charAt(currentDevice.length() - 1);
            lastChar = (char) (++lastChar);
            if ((int) lastChar > 122) {
                firstChars = getNext(firstChars);
                lastChar = 'a';
            }
            return firstChars + lastChar;
        }

    }
}
