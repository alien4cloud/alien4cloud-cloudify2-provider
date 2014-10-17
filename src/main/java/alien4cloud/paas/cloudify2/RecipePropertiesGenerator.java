package alien4cloud.paas.cloudify2;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Properties;

import javax.annotation.Resource;

import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.tosca.container.model.template.Capability;
import alien4cloud.tosca.container.model.template.PropertyValue;
import alien4cloud.tosca.container.model.template.Requirement;

import com.google.common.collect.Maps;

/**
 * Generates a properties file from node templates.
 * 
 * @author luc boutier
 */
@Component
public class RecipePropertiesGenerator {
    public static final String PROPERTIES_FILE_NAME = "service.properties";

    @Resource
    private ApplicationContext applicationContext;

    /**
     * Generates a properties file for the service based on a given PaaSNodeTemplate and fill-in the context with the property file path.
     * 
     * @param servicePath The path of the service
     * @param serviceRootTemplate The root node of the service.
     * @throws IOException
     */
    public void generatePropertiesFile(RecipeGeneratorServiceContext context, PaaSNodeTemplate serviceRootTemplate) throws IOException {
        Path descriptorPath = loadResourceFromClasspath("classpath:velocity/ServiceProperties.vm");

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

    protected Path loadResourceFromClasspath(String resource) throws IOException {
        URI uri = applicationContext.getResource(resource).getURI();
        String uriStr = uri.toString();
        Path path = null;
        if (uriStr.contains("!")) {
            FileSystem fs = null;
            try {
                String[] array = uriStr.split("!");
                fs = FileSystems.newFileSystem(URI.create(array[0]), new HashMap<String, Object>());
                path = fs.getPath(array[1]);

                // Hack to avoid classloader issues
                Path createTempFile = Files.createTempFile("velocity", ".vm");
                createTempFile.toFile().deleteOnExit();
                Files.copy(path, createTempFile, StandardCopyOption.REPLACE_EXISTING);

                path = createTempFile;
            } finally {
                if (fs != null) {
                    fs.close();
                }
            }
        } else {
            path = Paths.get(uri);
        }
        return path;
    }

    private void addProperties(RecipeGeneratorServiceContext context, PaaSNodeTemplate nodeTemplate, Properties properties, HashSet<String> processedNodes) {

        String serviceId = RecipeGenerator.serviceIdFromNodeTemplateId(nodeTemplate.getId());

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
                    for (Entry<String, PropertyValue> propEntry : capabilityEntry.getValue().getProperties().entrySet()) {
                        if (propEntry.getValue() != null) {
                            properties.put(serviceId + ".capabilities." + capabilityEntry.getKey() + "." + propEntry.getKey(), propEntry.getValue()
                                    .getValue());
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
                    for (Entry<String, PropertyValue> propEntry : requirementEntry.getValue().getProperties().entrySet()) {
                        if (propEntry.getValue() != null) {
                            properties.put(serviceId + ".requirements." + requirementEntry.getKey() + "." + propEntry.getKey(), propEntry.getValue()
                                    .getValue());
                        }
                    }
                }
            }
        }
    }
}