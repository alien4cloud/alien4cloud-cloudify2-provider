package alien4cloud.paas.cloudify2.utils;

import org.apache.commons.lang3.ArrayUtils;

import alien4cloud.model.components.IndexedToscaElement;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.tosca.ToscaUtils;

public class CloudifyPaaSUtils {

    private static final String PREFIX_SEPARATOR = "_";

    private CloudifyPaaSUtils() {
    }

    public static String serviceIdFromNodeTemplateId(final String nodeTemplateId) {
        return nodeTemplateId.toLowerCase().replaceAll(" ", "-");
    }

    /**
     * Find the name of the cloudify service that host the given node template.
     *
     * @param paaSNodeTemplate The node template for which to get the service name.
     * @return The id of the service that contains the node template.
     *
     */
    public static String cfyServiceNameFromNodeTemplate(final PaaSNodeTemplate paaSNodeTemplate) {
        return serviceIdFromNodeTemplateId(ToscaUtils.getHostTemplate(paaSNodeTemplate).getId());
    }

    /**
     * Compute the path of the node type of a node template relative to the service root directory.
     *
     * @param indexedToscaElement The element for which to generate and get it's directory relative path.
     * @return The relative path of the node template's type artifacts in the service directory.
     */
    public static String getNodeTypeRelativePath(final IndexedToscaElement indexedToscaElement) {
        return indexedToscaElement.getElementId() + "-" + indexedToscaElement.getArchiveVersion();
    }

    /**
     * prefix a string with another
     *
     * @param toPrefix
     * @param prefixes
     * @return
     */
    public static String prefixWith(String toPrefix, String... prefixes) {
        if (toPrefix == null) {
            return null;
        }
        if (ArrayUtils.isEmpty(prefixes)) {
            return toPrefix;
        }
        StringBuilder builder = new StringBuilder();
        for (String prefix : prefixes) {
            builder.append(prefix).append(PREFIX_SEPARATOR);
        }
        return builder.append(toPrefix).toString();
    }
}
