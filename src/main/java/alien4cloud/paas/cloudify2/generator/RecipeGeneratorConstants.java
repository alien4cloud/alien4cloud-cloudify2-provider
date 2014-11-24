package alien4cloud.paas.cloudify2.generator;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Class that contains cloudify service descriptor template constants
 * All the properties can be modified as needed by a template string engine
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class RecipeGeneratorConstants {
    /** Name of the application property string. */
    public static final String APPLICATION_NAME = "applicationName";

    /** Name of the application service [service in application] property string. */
    public static final String APPLICATION_SERVICE = "applicationService";

    public static final String APPLICATION_SERVICES = "applicationServices";

    /** Name of the service name property string. */
    public static final String SERVICE_NAME = "serviceName";

    /** Name of the service compute property string. */
    public static final String SERVICE_COMPUTE_TEMPLATE_NAME = "computeTemplateName";

    /** Name of the service num instances property string. */
    public static final String SERVICE_NUM_INSTANCES = "numInstances";

    /** Name of the service minimum allowed property string. */
    public static final String SERVICE_MIN_ALLOWED_INSTANCES = "minAllowedInstances";

    /** Name of the service maximum allowed property string. */
    public static final String SERVICE_MAX_ALLOWED_INSTANCES = "maxAllowedInstances";

    /** Name of the service property that contains the custom commands definitions (map) */
    public static final String SERVICE_CUSTOM_COMMANDS = "customCommands";

    /** Name of the service property that contains the start detection script. */
    public static final String SERVICE_START_DETECTION_COMMAND = "startDetectionCommand";

    /** Name of the scripts variable. */
    public static final String SCRIPTS = "scripts";

    /** Name of the lifecycle associated with the script file. */
    public static final String SCRIPT_LIFECYCLE = "scriptLifecycle";

    /** Name of properties map in the properties file. */
    public static final String SERVICE_PROPERTIES_MAP = "properties";

    /** Name of the service property that contains the stop detection script. */
    public static final String SERVICE_STOP_DETECTION_COMMAND = "stopDetectionCommand";

    /** Name of the service property that contains a detection script. */
    public static final String SERVICE_DETECTION_COMMAND = "detectionCommand";

    /** Name of the service property that contains a locator script. */
    public static final String LOCATORS = "locators";

    public static final String AND_OPERATOR = "&&";
    public static final String OR_OPERATOR = "||";

    public static final String CONTEXT_THIS_INSTANCE_ATTRIBUTES = "context.attributes.thisInstance";
    public static final String CONTEXT_THIS_SERVICE_ATTRIBUTES = "context.attributes.thisService";

    /** BlockStorages **/
    public static final String VOLUME_ID_KEY = "volumeId";
    public static final String PATH_KEY = "path";
    public static final String DEVICE_KEY = "device";
    public static final String FS_KEY = "fileSystem";

    /** Name of the service property that contains a poststart script. */
    public static final String POSTSTART_COMMAND = "postStartCommand";

    /** Name of the service property that contains a init script. */
    public static final String INIT_COMMAND = "initCommand";

    public static final String CREATE_COMMAND = "createCommand";
    public static final String CONFIGURE_COMMAND = "configureCommand";

    public static final String INIT_LIFECYCLE = "init";

    /** Name of the velocity property for formating a volume. */
    public static final String FORMAT_VOLUME_COMMAND = "formatVolumeCommand";

    public static final String GET_VOLUMEID_COMMAND = "getVolumeIdCommand";

    /** Name of the service property that contains a shutdown script. */
    public static final String SHUTDOWN_COMMAND = "shutdownCommand";
    public static final String SHUTDOWN_LIFECYCLE = "shutdown";
}