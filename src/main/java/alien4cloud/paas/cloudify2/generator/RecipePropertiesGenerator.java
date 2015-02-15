package alien4cloud.paas.cloudify2.generator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Properties;

import org.springframework.stereotype.Component;

import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.ScalarPropertyValue;
import alien4cloud.model.topology.Capability;
import alien4cloud.model.topology.Requirement;
import alien4cloud.paas.cloudify2.utils.CloudifyPaaSUtils;
import alien4cloud.paas.cloudify2.utils.VelocityUtil;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;

import com.google.common.collect.Maps;

/**
 * Generates a properties file from node templates.
 *
 * @author luc boutier
 */
@Component
public class RecipePropertiesGenerator {
    public static final String PROPERTIES_FILE_NAME = "service.properties";

    /**
     * Generates a properties file for the service based on a given PaaSNodeTemplate and fill-in the context with the property file path.
     *
     * @param context
     * @param serviceRootTemplate
     * @param descriptorPath
     * @throws IOException
     */
    public void generatePropertiesFile(RecipeGeneratorServiceContext context, PaaSNodeTemplate serviceRootTemplate, Path descriptorPath) throws IOException {

        Path propertiesFile = context.getServicePath().resolve(PROPERTIES_FILE_NAME);

        Properties props = new Properties();
        addProperties(context, serviceRootTemplate, props, new HashSet<String>());

        HashMap<String, Object> properties = Maps.newHashMap();
        for (Entry<Object, Object> propEntry : props.entrySet()) {
            properties.put((String) propEntry.getKey(), propEntry.getValue());
        }

        HashMap<String, Object> params = Maps.newHashMap();
        params.put("properties", properties);

        VelocityUtil.writeToOutputFile(descriptorPath, propertiesFile, params);

        context.setPropertiesFilePath(propertiesFile);
    }

    private void addProperties(RecipeGeneratorServiceContext context, PaaSNodeTemplate nodeTemplate, Properties properties, HashSet<String> processedNodes) {

        String serviceId = CloudifyPaaSUtils.serviceIdFromNodeTemplateId(nodeTemplate.getId());

        if (processedNodes.contains(serviceId)) {
            return;
        } else {
            processedNodes.add(serviceId);
        }

        if (nodeTemplate.getNodeTemplate().getProperties() != null) {
            for (Entry<String, String> propEntry : nodeTemplate.getNodeTemplate().getProperties().entrySet()) {
                if (propEntry.getValue() != null) {
                    properties.put(serviceId + "." + propEntry.getKey(), propEntry.getValue());
                }
            }
        }
        addRequirementsProperties(nodeTemplate, properties, serviceId);
        addCapabilitiesProperties(nodeTemplate, properties, serviceId);

        for (PaaSRelationshipTemplate relationshipTemplate : nodeTemplate.getRelationshipTemplates()) {
            PaaSNodeTemplate relationshipTarget = context.getNodeTemplateById(relationshipTemplate.getRelationshipTemplate().getTarget());
            addProperties(context, relationshipTarget, properties, processedNodes);
        }

        for (PaaSNodeTemplate childNodeTemplate : nodeTemplate.getChildren()) {
            addProperties(context, childNodeTemplate, properties, processedNodes);
        }

        if (nodeTemplate.getAttachedNode() != null) {
            addProperties(context, nodeTemplate.getAttachedNode(), properties, processedNodes);
        }
    }

    private void addCapabilitiesProperties(PaaSNodeTemplate nodeTemplate, Properties properties, String serviceId) {
        if (nodeTemplate.getNodeTemplate().getCapabilities() != null) {
            for (Entry<String, Capability> capabilityEntry : nodeTemplate.getNodeTemplate().getCapabilities().entrySet()) {
                if (capabilityEntry.getValue().getProperties() != null) {
                    for (Entry<String, AbstractPropertyValue> propEntry : capabilityEntry.getValue().getProperties().entrySet()) {
                        if (propEntry.getValue() != null && propEntry instanceof ScalarPropertyValue) {
                            properties.put(serviceId + ".capabilities." + capabilityEntry.getKey() + "." + propEntry.getKey(),
                                    ((ScalarPropertyValue) propEntry.getValue()).getValue());
                        }
                    }
                }
            }
        }
    }

    private void addRequirementsProperties(PaaSNodeTemplate nodeTemplate, Properties properties, String serviceId) {
        if (nodeTemplate.getNodeTemplate().getRequirements() != null) {
            for (Entry<String, Requirement> requirementEntry : nodeTemplate.getNodeTemplate().getRequirements().entrySet()) {
                if (requirementEntry.getValue().getProperties() != null) {
                    for (Entry<String, AbstractPropertyValue> propEntry : requirementEntry.getValue().getProperties().entrySet()) {
                        if (propEntry.getValue() != null && propEntry.getValue() instanceof ScalarPropertyValue) {
                            properties.put(serviceId + ".requirements." + requirementEntry.getKey() + "." + propEntry.getKey(),
                                    ((ScalarPropertyValue) propEntry.getValue()).getValue());
                        }
                    }
                }
            }
        }
    }
}