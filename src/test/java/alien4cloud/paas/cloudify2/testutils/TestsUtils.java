package alien4cloud.paas.cloudify2.testutils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.FileUtils;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.mapping.ElasticSearchClient;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import alien4cloud.component.repository.exception.CSARVersionAlreadyExistsException;
import alien4cloud.dao.ElasticSearchDAO;
import alien4cloud.git.RepositoryManager;
import alien4cloud.model.components.Csar;
import alien4cloud.model.topology.Topology;
import alien4cloud.security.Role;
import alien4cloud.tosca.ArchiveUploadService;
import alien4cloud.tosca.parser.ParsingError;
import alien4cloud.tosca.parser.ParsingErrorLevel;
import alien4cloud.tosca.parser.ParsingException;
import alien4cloud.tosca.parser.ParsingResult;
import alien4cloud.utils.FileUtil;
import alien4cloud.utils.YamlParserUtil;

@Component
@Slf4j
public class TestsUtils {
    protected static final String CSAR_SOURCE_PATH = "src/test/resources/csars/";
    private static final String TOPOLOGIES_PATH = "src/test/resources/topologies/";
    private static Path gitArtifactsDirectory = Paths.get("target/git-artifacts");
    private static Map<String, String[]> remoteGitArtifacts;

    @Resource
    private ArchiveUploadService archiveUploadService;

    @Resource
    private ElasticSearchClient esClient;

    static {
        remoteGitArtifacts = new HashMap<String, String[]>();
        remoteGitArtifacts.put("tosca-normative-types-1.0.0.wd03", new String[] { "https://github.com/alien4cloud/tosca-normative-types.git", "master" });
        remoteGitArtifacts.put("samples", new String[] { "https://github.com/alien4cloud/samples.git", "master" });
        remoteGitArtifacts.put("alien-extended-types", new String[] { "https://github.com/alien4cloud/alien4cloud-extended-types.git", "master" });
    }

    public void uploadGitArchive(String repository, String archiveDirectoryName) throws Exception {
        String path = gitArtifactsDirectory + "/" + repository;
        String[] urlAndBrach = remoteGitArtifacts.get(repository);
        if (!Files.exists(Paths.get(path)) && urlAndBrach != null) {
            (new RepositoryManager()).cloneOrCheckout(gitArtifactsDirectory, urlAndBrach[0], urlAndBrach[1], repository);
        }
        uploadCsarFile(path + "/" + archiveDirectoryName);
    }

    public void uploadCsarFile(String path) throws IOException, ParsingException, CSARVersionAlreadyExistsException {
        Authentication auth = new TestingAuthenticationToken(Role.ADMIN.name().toLowerCase(), "", Role.ADMIN.name());
        SecurityContextHolder.getContext().setAuthentication(auth);
        log.info("uploading archive " + path);
        Path inputPath = Paths.get(path);
        Path zipPath = Files.createTempFile("csar", ".zip");
        FileUtil.zip(inputPath, zipPath);
        ParsingResult<Csar> result = archiveUploadService.upload(zipPath);
        if (!result.getContext().getParsingErrors().isEmpty()) {
            log.info("Errors during upload of " + path);
            log.warn(result.getContext().getParsingErrors().toString());
            for (ParsingError error : result.getContext().getParsingErrors()) {
                if (error.getErrorLevel() == ParsingErrorLevel.ERROR) {
                    throw new ParsingException(result.getContext().getFileName(), result.getContext().getParsingErrors());
                }
            }
        }
    }

    public void uploadArchive(String name) throws IOException, CSARVersionAlreadyExistsException, ParsingException {
        uploadCsarFile(CSAR_SOURCE_PATH + name);
    }

    public Topology parseYamlTopology(String topologyFileName) throws IOException {
        Topology topology = YamlParserUtil.parseFromUTF8File(Paths.get(TOPOLOGIES_PATH + topologyFileName + ".yml"), Topology.class);
        topology.setId(UUID.randomUUID().toString());
        return topology;
    }

    public void cleanESFiles(List<Class<?>> indiceClassesToClean) throws Throwable {
        log.info("Cleaning repositories files");
        if (Files.exists(Paths.get("target/tmp/"))) {
            FileUtil.delete(Paths.get("target/tmp/"));
        }
        Path csarrepo = Paths.get("target/alien/csar");
        if (Files.exists(csarrepo)) {
            FileUtil.delete(csarrepo);
        }

        Files.createDirectories(csarrepo);

        // Clean elastic search cluster
        for (Class<?> indiceClass : indiceClassesToClean) {
            esClient.getClient().prepareDeleteByQuery(new String[] { indiceClass.getSimpleName().toLowerCase() }).setQuery(QueryBuilders.matchAllQuery())
                    .execute().get();
        }
        esClient.getClient().prepareDeleteByQuery(new String[] { ElasticSearchDAO.TOSCA_ELEMENT_INDEX }).setQuery(QueryBuilders.matchAllQuery()).execute()
                .get();
    }

    public static void cleanAlienTargetDir() {
        FileUtils.deleteQuietly(new File("target/alien"));
        FileUtils.deleteQuietly(gitArtifactsDirectory.toFile());
    }
}
