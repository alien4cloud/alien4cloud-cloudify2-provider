package alien4cloud.paas.cloudify2.utils;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import alien4cloud.model.components.IndexedToscaElement;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.rest.utils.JsonUtil;
import alien4cloud.tosca.ToscaUtils;
import alien4cloud.utils.MapUtil;

public class CloudifyPaaSUtils {

    private static final String HA_TEMPLATE_PAAS_ID_TEMPLATE = "_%s_AZ_%s";
    private static final String HA_TEMPLATE_PAAS_ID_REGEX = String.format(HA_TEMPLATE_PAAS_ID_TEMPLATE, ".*", ".*");// "\\w*_ALIEN_AZ_\\w*";
    public static final Pattern HA_TEMPLATE_PAAS_ID_PATTERN = Pattern.compile(HA_TEMPLATE_PAAS_ID_REGEX);

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
        return serviceIdFromNodeTemplateId(ToscaUtils.getMandatoryHostTemplate(paaSNodeTemplate).getId());
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

    public static String getAvailabilityZone(Map<String, Object> compute) {
        String az = null;
        az = (String) MapUtil.get((Map<String, Object>) compute, "locationId");
        if (StringUtils.isBlank(az)) {
            Map<String, Object> custom = (Map<String, Object>) MapUtil.get((Map<String, Object>) compute, "custom");
            if (custom != null) {
                az = (String) custom.get("openstack.compute.zone");
            }

            if (StringUtils.isBlank(az)) {
                Object availabilityZones = MapUtil.get((Map<String, Object>) compute, "availabilityZones");
                if (availabilityZones != null) {
                    List<String> azs;
                    try {
                        azs = JsonUtil.toList(JsonUtil.toString(availabilityZones), String.class);
                        az = azs.size() > 0 ? azs.get(0) : az;
                    } catch (Exception e) {
                    }
                }
            }
        }

        return az;
    }

    /**
     * Build a compute HA PaaSesource Id based on a basic compute PaaSResourceId and an Availability Zone Id
     *
     * @param paaSResourceId
     * @param aZPaaSResourceId
     * @return
     */
    public static String buildHAPaaSResourceId(String paaSResourceId, String aZPaaSResourceId) {
        return String.format(HA_TEMPLATE_PAAS_ID_TEMPLATE, paaSResourceId, aZPaaSResourceId);
    }
}
