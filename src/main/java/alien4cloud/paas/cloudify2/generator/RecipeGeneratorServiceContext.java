package alien4cloud.paas.cloudify2.generator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import lombok.Getter;
import lombok.Setter;

import org.apache.commons.lang3.StringUtils;

import alien4cloud.model.components.IndexedToscaElement;
import alien4cloud.paas.cloudify2.DefaultValues;
import alien4cloud.paas.cloudify2.utils.CloudifyPaaSUtils;
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
    /** Set of types (nodes and relationships) that are used in the recipe. */
    private final Set<String> recipeTypes = Sets.newHashSet();
    /** Maps nodeId -> script of the start detection commands that we have to aggregate to generate a good start detection for cloudify. */
    private final Map<String, String> startDetectionCommands = Maps.newHashMap();
    /** Maps commandUniqueName -> script of the custom commands that we have to add to the service recipe. */
    private final Map<String, String> customCommands = Maps.newHashMap();
    /** Maps commandUniqueName -> script of the relationships operations that we have to add to the service recipe as custom command. */
    private final Map<String, String> relationshipCustomCommands = Maps.newHashMap();
    /** Map of the node templates in the topology. */
    private final Map<String, PaaSNodeTemplate> allNodes;
    /** Maps nodeId -> script of the stop detection commands that we have to aggregate to generate a good stop detection for cloudify. */
    private final Map<String, String> stopDetectionCommands = Maps.newHashMap();
    /** Maps nodeId -> script of the process locators commands that we have to aggregate to generate a good process locator for cloudify. */
    private final Map<String, String> processLocatorsCommands = Maps.newHashMap();
    /** Maps name -> value of some additional properties needed in the service level, such as the reference of the startDetection / stopDetection file path */
    private final Map<String, String> additionalProperties = Maps.newHashMap();
    /** Maps <nodeName -> Map <artifactName, artifactPath> > of path of different artifacts of nodes */
    private final Map<String, Map<String, String>> nodeArtifactsPaths = Maps.newHashMap();
    /** Maps <elementId -> Path > of paths of directories of different tosca element */
    private final Map<String, Path> elementsRecipeDirectoryPaths = Maps.newHashMap();

    /** start detection timeout in second */
    private int startDetectionTimeoutInSec = DefaultValues.DEFAULT_START_DETECTION_TIMEOUT;

    /** Events lease time for this service */
    private Double eventsLeaseInHour = DefaultValues.DEFAULT_EVENTS_LEASE_IN_HOUR;

    /** Indicates if to delete the storages */
    private boolean deleteStorages = false;

    /**
     * Initialize a new context for service recipe generation.
     *
     * @param allNodes The map of all the nodes templates in the topology.
     */
    public RecipeGeneratorServiceContext(Map<String, PaaSNodeTemplate> allNodes) {
        this.allNodes = allNodes;
    }

    /**
     * Get a node template from it's id.
     *
     * @param nodeTemplateId The id of the node template to get.
     * @return The node template in the recipe that match the given id or null if no node template exists for the given id.
     */
    public PaaSNodeTemplate getNodeTemplateById(String nodeTemplateId) {
        return allNodes.get(nodeTemplateId);
    }

    /**
     * Get the recipe directory of a tosca element. Creates it if not already created
     *
     * @param indexedToscaElement
     * @return The tosca element directory path
     * @throws IOException
     */
    public Path getRecipeDirectoryPath(IndexedToscaElement indexedToscaElement) throws IOException {
        Path nodeTypePath = elementsRecipeDirectoryPaths.get(indexedToscaElement.getId());
        if (nodeTypePath == null && servicePath != null) {
            String nodeTypeRelativePath = CloudifyPaaSUtils.getNodeTypeRelativePath(indexedToscaElement);
            nodeTypePath = servicePath.resolve(nodeTypeRelativePath);
            Files.createDirectories(nodeTypePath);
            elementsRecipeDirectoryPaths.put(indexedToscaElement.getId(), nodeTypePath);
        }
        return nodeTypePath;
    }

    public void setEventsLeaseInHour(String valueInHour) {
        if (StringUtils.isNotBlank(valueInHour) && Double.parseDouble(valueInHour) > 0) {
            this.eventsLeaseInHour = Double.parseDouble(valueInHour);
        }
    }

    public void setDeleteStorages(String deletableBlockStorage) {
        if (StringUtils.isNotBlank(deletableBlockStorage) && Boolean.parseBoolean(deletableBlockStorage)) {
            this.deleteStorages = Boolean.parseBoolean(deletableBlockStorage);
        }
    }

    public void setStartDetectionTimeoutInSec(String startDetectionTimeoutInSec) {
        if (StringUtils.isNotBlank(startDetectionTimeoutInSec) && Integer.parseInt(startDetectionTimeoutInSec) > 0) {
            this.startDetectionTimeoutInSec = Integer.parseInt(startDetectionTimeoutInSec);
        }
    }

    public int getStartExecutionTimeout() {
        return startDetectionTimeoutInSec * 9 / 10;
    }
}