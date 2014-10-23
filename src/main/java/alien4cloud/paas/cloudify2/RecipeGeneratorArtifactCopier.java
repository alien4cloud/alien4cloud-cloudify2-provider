package alien4cloud.paas.cloudify2;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import alien4cloud.component.model.IndexedArtifactToscaElement;
import alien4cloud.component.repository.ArtifactLocalRepository;
import alien4cloud.component.repository.ArtifactRepositoryConstants;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.tosca.container.model.template.DeploymentArtifact;

import com.google.common.collect.Maps;

@Slf4j
@Component
public class RecipeGeneratorArtifactCopier {
    private static final String OVERRIDES_DIR_NAME = "overrides";
    private static final String DIRECTORY_ARTIFACT_TYPE = "fastconnect.artifacts.ResourceDirectory";
    private static final String SERVICE_DIRECTORY_TEMPLATE = "serviceDirectory}";

    @Resource
    private ArtifactLocalRepository localRepository;

    /**
     * Copy all artifacts for the nodes and relationships in the topology from a defined rootNode.
     *
     * @param context The recipe generator context.
     * @param rootNode The root node for which to copy artifacts.
     * @throws IOException In case we fail to copy files.
     */
    public void copyAllArtifacts(RecipeGeneratorServiceContext context, PaaSNodeTemplate rootNode) throws IOException {
        copyDeploymentArtifacts(context, rootNode.getCsarPath(), rootNode.getId(), rootNode.getIndexedNodeType(), rootNode.getNodeTemplate().getArtifacts());
        for (PaaSRelationshipTemplate relationship : rootNode.getRelationshipTemplates()) {
            copyDeploymentArtifacts(context, relationship.getCsarPath(), null, relationship.getIndexedRelationshipType(), null);
        }
        if (rootNode.getChildren() != null) {
            for (PaaSNodeTemplate childNode : rootNode.getChildren()) {
                copyAllArtifacts(context, childNode);
            }
        }
    }

    /**
     * Copy a deployment artifact from the CSAR repository or other location into the generated cloudify recipe.
     *
     * @param context The context of the recipe generation that contains the path of the service as well as the list of node types that have been already
     *            managed for this service recipe.
     * @param csarPath Path to the CSAR that contains the node or relationship for which to copy artifacts.
     * @param indexedToscaElement The indexed TOSCA element (node or relationship) for which to copy the artifacts.
     * @param overrideArtifacts The map of artifacts that have been overridden in the topology.
     * @return
     * @throws IOException In case there is an IO error while performing the artifacts copy.
     */
    public void copyDeploymentArtifacts(RecipeGeneratorServiceContext context, Path csarPath, String nodeId, IndexedArtifactToscaElement indexedToscaElement,
            Map<String, DeploymentArtifact> overrideArtifacts) throws IOException {

        Map<String, DeploymentArtifact> artifacts = indexedToscaElement.getArtifacts();
        Map<String, Path> artifactsPaths = null;
        if (artifacts != null) {
            // create a folder for this node type
            String nodeTypeRelativePath = RecipeGenerator.getNodeTypeRelativePath(indexedToscaElement);
            Path nodeTypePath = context.getServicePath().resolve(nodeTypeRelativePath);
            Files.createDirectories(nodeTypePath);

            // copy the properties file
            copyPropertiesFile(context.getPropertiesFilePath(), nodeTypePath.resolve(RecipePropertiesGenerator.PROPERTIES_FILE_NAME));

            // copy the node type artifacts to the given folder
            artifactsPaths = Maps.newHashMap();
            for (Map.Entry<String, DeploymentArtifact> artifactEntry : artifacts.entrySet()) {
                // artifact may be overridden at the template level
                DeploymentArtifact artifact = null;
                String artifactTarget = null;
                if (overrideArtifacts != null) {
                    DeploymentArtifact tempArti = overrideArtifacts.get(artifactEntry.getKey());
                    if (tempArti != null && StringUtils.isNotBlank(tempArti.getArtifactRepository())) {
                        artifact = tempArti;
                        artifactTarget = artifact != null ? OVERRIDES_DIR_NAME + "-" + nodeId + File.separator + artifact.getArtifactRef() : artifactTarget;
                    }
                    // artifact = overrideArtifacts.get(artifactEntry.getKey());
                    // artifactTarget = artifact != null ? OVERRIDES_DIR_NAME + "-" + nodeId + File.separator + artifact.getArtifactRef() : artifactTarget;
                }
                if (artifact == null) {
                    artifact = artifactEntry.getValue();
                    artifactTarget = artifact.getArtifactRef();
                }
                if (artifact != null && StringUtils.isNotBlank(artifactTarget)) {
                    Path copyPath = copyArtifact(csarPath, nodeTypePath, nodeTypeRelativePath, artifactTarget, artifact);
                    artifactsPaths.put(artifactEntry.getKey(), context.getServicePath().relativize(copyPath));
                }
            }
        }

        // done!
        context.getRecipeTypes().add(indexedToscaElement.getId());
        if (nodeId != null && MapUtils.isNotEmpty(artifactsPaths)) {
            context.getNodeArtifactsPaths().put(nodeId, artifactsPaths);
        }
    }

    private void copyPropertiesFile(Path source, Path dest) throws IOException {
        if (Files.exists(dest)) {
            return;
        }
        Files.copy(source, dest);
    }

    private Path copyArtifact(final Path csarPath, final Path nodeTypePath, final String nodeTypeRelativePath, String target, final DeploymentArtifact artifact)
            throws IOException {
        final Path targetPath = nodeTypePath.resolve(target);

        if (Files.notExists(targetPath)) {
            String convertedPath = Paths.get(targetPath.toString()).toString();
            int lastSeptatorIndex = convertedPath.lastIndexOf(File.separator);
            Path targetContainerDirPath = null;
            if (lastSeptatorIndex > 0) {
                targetContainerDirPath = nodeTypePath.resolve(convertedPath.substring(0, lastSeptatorIndex));
            }
            // copy the artifact to the target destination
            if (ArtifactRepositoryConstants.ALIEN_ARTIFACT_REPOSITORY.equals(artifact.getArtifactRepository())) {
                if (targetContainerDirPath != null) {
                    Files.createDirectories(targetContainerDirPath);
                }

                Files.copy(localRepository.resolveFile(artifact.getArtifactRef()), targetPath);
                replaceCloudifyServicePath(targetPath, nodeTypeRelativePath);
            } else {
                FileSystem csarFileSystem = null;
                try {
                    csarFileSystem = FileSystems.newFileSystem(csarPath, null);
                    // the artifact is expected to be in the archive
                    // Path artifactPath = csarFileSystem.getPath(target);
                    Path artifactPath = csarFileSystem.getPath(target);
                    // this may be actually a folder...
                    // TODO refactor this in the FileUtils maybe.

                    if (targetContainerDirPath != null) {
                        Files.createDirectories(targetContainerDirPath);
                    }

                    if (DIRECTORY_ARTIFACT_TYPE.equals(artifact.getArtifactType())) {
                        Files.walkFileTree(artifactPath, new SimpleFileVisitor<Path>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                                Path destFile = nodeTypePath.resolve(file.toString().substring(1));
                                File dest = destFile.toFile();
                                dest.mkdirs();
                                dest.createNewFile();
                                if (log.isDebugEnabled()) {
                                    log.debug(String.format("Extracting file %s to %s", file, destFile.toAbsolutePath()));
                                }
                                Files.copy(file, destFile, StandardCopyOption.REPLACE_EXISTING);
                                replaceCloudifyServicePath(destFile, nodeTypeRelativePath);
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    } else {
                        Files.copy(artifactPath, targetPath);
                    }
                } finally {
                    if (csarFileSystem != null) {
                        csarFileSystem.close();
                    }
                }
            }
        }
        return targetPath;
    }

    private void replaceCloudifyServicePath(Path script, final String nodeTypeRelativePath) throws IOException {
        if (script.toString().endsWith(".groovy")) {
            String content = new String(Files.readAllBytes(script), StandardCharsets.UTF_8);
            if (content.contains(SERVICE_DIRECTORY_TEMPLATE)) {
                content = content.replaceAll(SERVICE_DIRECTORY_TEMPLATE, SERVICE_DIRECTORY_TEMPLATE + "/" + nodeTypeRelativePath);
                Files.write(script, content.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}
