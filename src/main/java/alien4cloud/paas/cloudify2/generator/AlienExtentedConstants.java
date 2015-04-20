package alien4cloud.paas.cloudify2.generator;

public final class AlienExtentedConstants {
    // interfaces
    public static final String CLOUDIFY_EXTENSIONS_INTERFACE_NAME = "fastconnect.cloudify.extensions";
    public static final String STANDARD_INTERFACE_NAME = "tosca.interfaces.node.lifecycle.Standard";

    // operations
    public static final String CLOUDIFY_EXTENSIONS_START_DETECTION_OPERATION_NAME = "start_detection";
    public static final String CLOUDIFY_EXTENSIONS_STOP_DETECTION_OPERATION_NAME = "stop_detection";
    public static final String CLOUDIFY_EXTENSIONS_LOCATOR_OPERATION_NAME = "locator";

    // artifacts
    public static final String SHELL_ARTIFACT_TYPE = "tosca.artifacts.ShellScript";
    public static final String GROOVY_ARTIFACT_TYPE = "tosca.artifacts.GroovyScript";
    public static final String BATCH_ARTIFACT_TYPE = "alien.artifacts.BatchScript";

    // attributes
    public static final String IP_ADDRESS = "ip_address";

}
