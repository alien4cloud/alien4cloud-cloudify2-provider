package alien4cloud.paas.cloudify2.generator;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import alien4cloud.component.CSARRepositorySearchService;
import alien4cloud.component.repository.ArtifactLocalRepository;
import alien4cloud.component.repository.ArtifactRepositoryConstants;
import alien4cloud.component.repository.CsarFileRepository;
import alien4cloud.component.repository.exception.CSARVersionNotFoundException;
import alien4cloud.dao.IGenericSearchDAO;
import alien4cloud.exception.NotFoundException;
import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.components.ImplementationArtifact;
import alien4cloud.model.components.IndexedArtifactToscaElement;
import alien4cloud.paas.cloudify2.utils.CloudifyPaaSUtils;
import alien4cloud.paas.exception.PaaSDeploymentException;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;

import com.google.common.collect.Maps;

@Slf4j
@Component
public class RecipeGeneratorArtifactCopier {
    private static final String OVERRIDES_DIR_NAME = "overrides";
    private static final String DIRECTORY_ARTIFACT_TYPE = "fastconnect.artifacts.ResourceDirectory";
    private static final String FILE_TYPE = "tosca.artifacts.File";
    private static final String SERVICE_DIRECTORY_TEMPLATE = "serviceDirectory}";

    @Resource
    private ArtifactLocalRepository localRepository;
    @Resource
    private CsarFileRepository fileRepository;
    @Resource(name = "alien-es-dao")
    private IGenericSearchDAO searchDAO;
    @Resource
    protected CSARRepositorySearchService csarRepositorySearchService;

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

        // process children
        if (rootNode.getChildren() != null) {
            for (PaaSNodeTemplate childNode : rootNode.getChildren()) {
                copyAllArtifacts(context, childNode);
            }
        }

        // process attached nodes
        if (rootNode.getAttachedNode() != null) {
            copyAllArtifacts(context, rootNode.getAttachedNode());
        }
    }

    /**
     * Copy a deployment artifact from the CSAR repository or other location into the generated cloudify recipe.
     *
     * @param context The context of the recipe generation that contains the path of the service as well as the list of node types that have been already
     *            managed for this service recipe.
     * @param csarPath Path to the CSAR that contains the node or relationship for which to copy artifacts.
     * @param nodeId the id of the node processed
     * @param indexedToscaElement The indexed TOSCA element (node or relationship) for which to copy the artifacts.
     * @param overrideArtifacts The map of artifacts that have been overridden in the topology.
     * @return
     * @throws IOException In case there is an IO error while performing the artifacts copy.
     */
    private void copyDeploymentArtifacts(RecipeGeneratorServiceContext context, Path csarPath, String nodeId, IndexedArtifactToscaElement indexedToscaElement,
            Map<String, DeploymentArtifact> overrideArtifacts) throws IOException {

        Map<String, DeploymentArtifact> artifacts = indexedToscaElement.getArtifacts();
        Map<String, String> artifactsPaths = null;
        if (artifacts != null) {
            // create a folder for this node type
            String nodeTypeRelativePath = CloudifyPaaSUtils.getNodeTypeRelativePath(indexedToscaElement);
            Path nodeTypePath = context.getServicePath().resolve(nodeTypeRelativePath);
            Files.createDirectories(nodeTypePath);

            // copy the properties file
            copyPropertiesFile(context.getPropertiesFilePath(), nodeTypePath.resolve(RecipePropertiesGenerator.PROPERTIES_FILE_NAME));

            // copy the node type artifacts to the given folder
            artifactsPaths = Maps.newHashMap();
            for (Map.Entry<String, DeploymentArtifact> artifactEntry : artifacts.entrySet()) {
                DeploymentArtifact artifact = artifactEntry.getValue();
                String artifactTarget = artifact.getArtifactRef();

                // artifact may be overridden at the template level
                if (overrideArtifacts != null) {
                    DeploymentArtifact tempArti = overrideArtifacts.get(artifactEntry.getKey());
                    if (tempArti != null && ArtifactRepositoryConstants.ALIEN_ARTIFACT_REPOSITORY.equals(tempArti.getArtifactRepository())) {
                        artifact = tempArti;
                        artifactTarget = OVERRIDES_DIR_NAME + "-" + nodeId + File.separator + artifact.getArtifactName();
                    }
                }

                if (artifact != null && StringUtils.isNotBlank(artifactTarget)) {
                    Path copyPath = copyArtifact(csarPath, nodeTypePath, nodeTypeRelativePath, artifactTarget, artifact, indexedToscaElement);
                    artifactsPaths.put(artifactEntry.getKey(), context.getServicePath().relativize(copyPath).toString());
                }
            }
        }

        // done!
        context.getRecipeTypes().add(indexedToscaElement.getId());
        if (nodeId != null && MapUtils.isNotEmpty(artifactsPaths)) {
            context.getNodeArtifactsPaths().put(nodeId, artifactsPaths);
        }
    }

    /**
     *
     *
     * /**
     * Copy an implementation artifact from the CSAR into the generated cloudify recipe.
     *
     * @param context The context of the recipe generation that contains the path of the service as well as the list of node types that have been already
     *            managed for this service recipe.
     * @param csarPath Path to the CSAR that contains the node or relationship for which to copy artifacts.
     * @param nodeTypeRelativePath The relative path of the node in which is defined the implementation artifact to copy
     * @param implementationArtifact The implementation artifact to copy
     * @param indexedToscaElement The tosca element from which the artifact is coming
     * @throws IOException In case there is an IO error while performing the artifacts copy
     */
    public void copyImplementationArtifact(RecipeGeneratorServiceContext context, Path csarPath, String nodeTypeRelativePath,
            ImplementationArtifact implementationArtifact, IndexedArtifactToscaElement indexedToscaElement) throws IOException {

        Path nodeTypePath = context.getServicePath().resolve(nodeTypeRelativePath);
        Files.createDirectories(nodeTypePath);
        // copy the properties file
        copyPropertiesFile(context.getPropertiesFilePath(), nodeTypePath.resolve(RecipePropertiesGenerator.PROPERTIES_FILE_NAME));
        DeploymentArtifact artifact = getDeploymentArtifact(implementationArtifact);
        copyArtifact(csarPath, nodeTypePath, nodeTypeRelativePath, artifact.getArtifactRef(), artifact, indexedToscaElement);
    }

    private DeploymentArtifact getDeploymentArtifact(ImplementationArtifact implementationArtifact) {
        DeploymentArtifact deploymentArtifact = new DeploymentArtifact();
        deploymentArtifact.setArtifactType(implementationArtifact.getArtifactType());
        deploymentArtifact.setArtifactRef(implementationArtifact.getArtifactRef());

        return deploymentArtifact;
    }

    private void copyPropertiesFile(Path source, Path dest) throws IOException {
        if (Files.exists(dest)) {
            return;
        }
        Files.copy(source, dest);
    }

    private Path copyArtifact(final Path csarPath, final Path nodeTypePath, final String nodeTypeRelativePath, String target,
            final DeploymentArtifact artifact, IndexedArtifactToscaElement indexedToscaElement) throws IOException {
        final Path targetPath = nodeTypePath.resolve(target);

        if (Files.notExists(targetPath)) {
            Files.createDirectories(targetPath.getParent());
            // if it is an alien repo artifact (override case) copy the artifact to the target destination
            if (ArtifactRepositoryConstants.ALIEN_ARTIFACT_REPOSITORY.equals(artifact.getArtifactRepository())) {
                Files.copy(localRepository.resolveFile(artifact.getArtifactRef()), targetPath);
                replaceCloudifyServicePath(targetPath, nodeTypeRelativePath);
            } else {
                // copy from the csar path
                copyArtifactFromCsar(csarPath, nodeTypePath, nodeTypeRelativePath, target, artifact, indexedToscaElement);
            }
        }

        if (Files.notExists(targetPath)) {
            throw new NotFoundException("Artifact reference file <" + artifact.getArtifactRef() + "> of tosca element <" + indexedToscaElement.getId()
                    + "> not found in Alien4Cloud");
        }
        return targetPath;
    }

    private void copyArtifactFromCsar(final Path csarPath, final Path nodeTypePath, final String nodeTypeRelativePath, String target,
            final DeploymentArtifact artifact, IndexedArtifactToscaElement indexedToscaElement) throws IOException {
        // try copy from direct parent first
        boolean processed = false;
        while (CollectionUtils.isNotEmpty(indexedToscaElement.getDerivedFrom()) && !processed) {
            IndexedArtifactToscaElement directParent = csarRepositorySearchService.getParentOfElement(IndexedArtifactToscaElement.class, indexedToscaElement,
                    indexedToscaElement.getDerivedFrom().get(0));
            Path directParentCsarPath;
            try {
                directParentCsarPath = fileRepository.getCSAR(directParent.getArchiveName(), directParent.getArchiveVersion());
            } catch (CSARVersionNotFoundException e) {
                throw new PaaSDeploymentException("Failed to copy artifact.", e);
            }
            copyArtifactFromCsar(directParentCsarPath, nodeTypePath, nodeTypeRelativePath, target, artifact, directParent);
            processed = true;
        }
        FileSystem csarFileSystem = null;
        try {
            csarFileSystem = FileSystems.newFileSystem(csarPath, null);
            // the artifact is expected to be in the archive. if not, do nothing
            Path artifactPath = csarFileSystem.getPath(target);
            if (Files.exists(artifactPath)) {
                // this may be actually a folder...
                // TODO refactor this in the FileUtils maybe.
                if (FILE_TYPE.equals(artifact.getArtifactType()) || DIRECTORY_ARTIFACT_TYPE.equals(artifact.getArtifactType())) {
                    Files.walkFileTree(artifactPath, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            Path destFile = null;
                            if (Paths.get(file.toString()).toString().startsWith(File.separator)) {
                                destFile = nodeTypePath.resolve(file.toString().substring(1));
                            } else {
                                destFile = nodeTypePath.resolve(file.toString());
                            }
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
                    Files.copy(artifactPath, nodeTypePath.resolve(target));
                }
            }
        } finally {
            if (csarFileSystem != null) {
                csarFileSystem.close();
            }
        }
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
