package alien4cloud.paas.cloudify2.generator;

import static alien4cloud.paas.cloudify2.generator.RecipeGeneratorConstants.CONTEXT_THIS_INSTANCE_ATTRIBUTES;
import static alien4cloud.paas.cloudify2.generator.RecipeGeneratorConstants.CONTEXT_THIS_SERVICE_ATTRIBUTES;
import static alien4cloud.paas.cloudify2.generator.RecipeGeneratorConstants.SHUTDOWN_COMMAND;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import lombok.Getter;
import lombok.Setter;
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
import alien4cloud.paas.cloudify2.ProviderLogLevel;
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
    private static final String LOCATION_SUFFIX = "data";
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
        SortedMap<String, String> startUpScripts = Maps.newTreeMap();
        boolean multiStorages = storageNodes.size() > 1;
        DeviceManager deviceManager = new DeviceManager();

        // to avoid conflict with generated devices
        registerProvidedDevices(storageNodes, deviceManager);

        for (PaaSNodeTemplate storageNode : storageNodes) {
            generateInitVolumeIdsScript(context, storageNode, storageNames.get(storageNode.getId()), initScripts);

            String defaultLocation = !multiStorages ? DEFAULT_BLOCKSTORAGE_LOCATION : "/" + CloudifyPaaSUtils.serviceIdFromNodeTemplateId(storageNode.getId())
                    + "_" + LOCATION_SUFFIX;
            generateStartUpStorageScript(context, storageNode, availabilityZone, startUpScripts, defaultLocation, deviceManager);
        }

        // execute init in parallel
        executions.add(commandGenerator.getParallelCommand(initScripts, null));

        // execute startup sequentially, sorted by device asc
        for (Entry<String, String> script : startUpScripts.entrySet()) {
            executions.add(commandGenerator.getGroovyCommand(script.getValue(), ProviderLogLevel.INFO));
        }
    }

    /**
     * register provided devices to avoid conflict with auto generated ones
     *
     * @param storageNodes
     * @param deviceManager
     * @throws PaaSDeploymentException
     */
    private void registerProvidedDevices(List<PaaSNodeTemplate> storageNodes, DeviceManager deviceManager) throws PaaSDeploymentException {
        for (PaaSNodeTemplate storageNode : storageNodes) {
            String providedDevice = getProperty(storageNode, NormativeBlockStorageConstants.DEVICE);
            if (StringUtils.isNotBlank(providedDevice)) {
                if (deviceManager.isAlreadyAssigned(providedDevice)) {
                    throw new PaaSDeploymentException("Failed to generate scripts for BlockStorage <" + storageNode.getId() + ">. Provided device <"
                            + providedDevice + "> is used by more than one node.");
                }
                deviceManager.register(storageNode.getId(), providedDevice);
            }
        }

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
            List<String> executions) throws IOException {
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
            Map<String, String> executions, String defaultLocation, DeviceManager deviceManager) throws IOException {
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
        String createAttachCommand = getStorageCreateAttachCommand(context, storageNode, deviceManager);
        velocityProps.put(RecipeGeneratorConstants.CREATE_COMMAND, createAttachCommand);

        // format and mount script
        String formatMountCommant = getStorageFormatMountCommand(context, storageNode, defaultLocation);
        velocityProps.put(RecipeGeneratorConstants.CONFIGURE_COMMAND, formatMountCommant);

        // generate startup BS
        String fileName = STORAGE_SCRIPTS_DIR + "/" + STORAGE_STARTUP_FILE_NAME + "-" + CloudifyPaaSUtils.serviceIdFromNodeTemplateId(storageNode.getId());
        generateScriptWorkflow(context.getServicePath(), startupBlockStorageScriptDescriptorPath, fileName, null, velocityProps);

        // we add in the execution map: device ==> ScriptFileName
        // we will need it to sort by device for sequential exec
        // Note that device is supposed to be unique per storage (a validation is done before passing here)
        executions.put(deviceManager.getRegisteredFor(storageNode.getId()), fileName.concat(".groovy"));
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

    private String getStorageCreateAttachCommand(final RecipeGeneratorServiceContext context, PaaSNodeTemplate storageNode, DeviceManager deviceManager)
            throws IOException {
        ExecEnvMaps envMaps = new ExecEnvMaps();
        String nodeVolumeIdKey = AlienUtils.prefixWith(AlienConstants.ATTRIBUTES_NAME_SEPARATOR, VOLUME_ID_KEY, storageNode.getId());
        String nodeStorageKey = AlienUtils.prefixWith(AlienConstants.ATTRIBUTES_NAME_SEPARATOR, STORAGE_TEMPLATE_KEY, storageNode.getId());
        envMaps.runtimes.put(VOLUME_ID_KEY, CONTEXT_THIS_INSTANCE_ATTRIBUTES + "[\"" + nodeVolumeIdKey + "\"]");
        envMaps.runtimes.put(STORAGE_TEMPLATE_KEY, CONTEXT_THIS_SERVICE_ATTRIBUTES + "[\"" + nodeStorageKey + "\"]");

        String createAttachCommand = getOperationCommandFromInterface(context, storageNode, ToscaNodeLifecycleConstants.STANDARD,
                ToscaNodeLifecycleConstants.CREATE, envMaps, null);

        String device = deviceManager.getRegisteredFor(storageNode.getId());

        // if no custom management then generate the default routine
        if (StringUtils.isBlank(createAttachCommand)) {
            device = StringUtils.isNotBlank(device) ? device : deviceManager.getNextAvailable();
            String fileName = STORAGE_SCRIPTS_DIR + "/" + DEFAULT_STORAGE_CREATE_FILE_NAME + "-"
                    + CloudifyPaaSUtils.serviceIdFromNodeTemplateId(storageNode.getId());

            Map<String, String> velocityProps = Maps.newHashMap();
            velocityProps.put(STORAGE_ID_KEY, storageNode.getId());
            generateScriptWorkflow(context.getServicePath(), createAttachBlockStorageScriptDescriptorPath, fileName, null, velocityProps);

            createAttachCommand = commandGenerator.getGroovyCommand(fileName.concat(".groovy"), envMaps.runtimes,
                    MapUtil.newHashMap(new String[] { DEVICE_KEY }, new String[] { device }), null, null);
        }
        // register the device for later sorting purpose and/or avoiding conflicts
        deviceManager.register(storageNode.getId(), device);
        return createAttachCommand;
    }

    private String getProperty(PaaSNodeTemplate storageNode, String propertyName) {
        Map<String, AbstractPropertyValue> properties = storageNode.getNodeTemplate().getProperties();
        String value = FunctionEvaluator.getScalarValue(MapUtils.getObject(properties, propertyName));
        return value;
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

    @Getter
    @Setter
    private class DeviceManager {
        private int increment = 0;
        private String lastGenerated;

        private Map<String, String> assignedDevices = Maps.newHashMap();

        public String getNextAvailable() {
            do {
                lastGenerated = getNext(lastGenerated);
            } while (isAlreadyAssigned(lastGenerated));
            return lastGenerated;
        }

        public void register(String nodeId, String device) {
            if (StringUtils.isBlank(device)) {
                device = "__" + increment++;
            }
            assignedDevices.put(nodeId, device);
        }

        public String getRegisteredFor(String nodeId) {
            return assignedDevices.get(nodeId);
        }

        public boolean isAlreadyAssigned(String device) {
            return assignedDevices.containsValue(device);
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
