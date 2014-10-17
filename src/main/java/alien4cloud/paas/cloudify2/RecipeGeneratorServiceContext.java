package alien4cloud.paas.cloudify2;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;
import alien4cloud.paas.model.PaaSNodeTemplate;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Context for the generation of a service recipe.
 */
@Getter
@Setter
public class RecipeGeneratorServiceContext {
    /** Id of the current service. */
    private String serviceId;
    /** Path of the directory for the service recipe. */
    private Path servicePath;
    /** Path of the generated properties file. */
    private Path propertiesFilePath;
    /** Set of types (nodes and relationships) that are used in the recipe. */
    private final Set<String> recipeTypes = Sets.newHashSet();
    /** Maps nodeId -> script of the start detection commands that we have to aggregate to generate a good start detection for cloudify. */
    private final Map<String, String> startDetectionCommands = Maps.newHashMap();
    /** Maps operation -> script of the custom commands that we have to add to the service recipe. */
    private final Map<String, String> customCommands = Maps.newHashMap();
    /** Map of the node templates in the topology. */
    private final Map<String, PaaSNodeTemplate> topologyNodeTemplates;
    /** Maps nodeId -> script of the stop detection commands that we have to aggregate to generate a good stop detection for cloudify. */
    private final Map<String, String> stopDetectionCommands = Maps.newHashMap();
    /** Maps nodeId -> script of the process locators commands that we have to aggregate to generate a good process locator for cloudify. */
    private final Map<String, String> processLocatorsCommands = Maps.newHashMap();
    /**
     * Maps name -> value of some additional properties needed in the service level, such as the reference of the startDetection / stopDetection file path
     */
    private final Map<String, String> additionalProperties = Maps.newHashMap();

    /**
     * Maps <nodeName -> Map <artifactName, artifactPath> > of path of different artifacts of nodes
     */
    private final Map<String, Map<String, Path>> nodeArtifactsPaths = Maps.newHashMap();

    /**
     * Initialize a new context for service recipe generation.
     * 
     * @param topologyNodeTemplates The map of all the nodes templates in the topology.
     */
    public RecipeGeneratorServiceContext(Map<String, PaaSNodeTemplate> topologyNodeTemplates) {
        this.topologyNodeTemplates = topologyNodeTemplates;
    }

    /**
     * Get a node template from it's id.
     * 
     * @param nodeTemplateId The id of the node template to get.
     * @return The node template in the recipe that match the given id or null if no node template exists for the given id.
     */
    public PaaSNodeTemplate getNodeTemplateById(String nodeTemplateId) {
        return topologyNodeTemplates.get(nodeTemplateId);
    }
}