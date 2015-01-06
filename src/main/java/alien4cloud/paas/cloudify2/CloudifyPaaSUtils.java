package alien4cloud.paas.cloudify2;

import alien4cloud.component.model.IndexedToscaElement;
import alien4cloud.paas.exception.PaaSTechnicalException;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.tosca.ToscaUtils;
import alien4cloud.tosca.container.model.NormativeComputeConstants;

public class CloudifyPaaSUtils {

    private static final String prefixSeparator = "_";

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
        return serviceIdFromNodeTemplateId(getHostTemplate(paaSNodeTemplate).getId());
    }

    public static PaaSNodeTemplate getHostTemplate(final PaaSNodeTemplate paaSNodeTemplate) {
        PaaSNodeTemplate parent = paaSNodeTemplate;
        while (parent != null) {
            if (ToscaUtils.isFromType(NormativeComputeConstants.COMPUTE_TYPE, parent.getIndexedNodeType())) {
                return parent;
            }
            parent = parent.getParent();
        }
        throw new PaaSTechnicalException("Cannot get the service name: The node template <" + paaSNodeTemplate.getId()
                + "> is not declared as hosted on a compute.");
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
     * @param prefix
     * @return
     */
    public static String prefixWithTemplateId(String toPrefix, String templateId) {
        if (toPrefix == null) {
            return null;
        }
        if (templateId == null) {
            return toPrefix;
        }

        return templateId.concat(prefixSeparator).concat(toPrefix);
    }
}
