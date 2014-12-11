package alien4cloud.paas.cloudify2.testutils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.io.FileUtils;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.mapping.ElasticSearchClient;
import org.springframework.stereotype.Component;

import alien4cloud.component.repository.exception.CSARVersionAlreadyExistsException;
import alien4cloud.dao.ElasticSearchDAO;
import alien4cloud.tosca.ArchiveUploadService;
import alien4cloud.tosca.container.model.topology.Topology;
import alien4cloud.tosca.parser.ParsingException;
import alien4cloud.utils.FileUtil;
import alien4cloud.utils.YamlParserUtil;

@Component
@Slf4j
public class TestsUtils {
    protected static final String CSAR_SOURCE_PATH = "src/test/resources/csars/";
    private static final String TOPOLOGIES_PATH = "src/test/resources/topologies/";

    @Resource
    private ArchiveUploadService archiveUploadService;

    @Resource
    private ElasticSearchClient esClient;

    public void uploadCsar(String path) throws IOException, ParsingException, CSARVersionAlreadyExistsException {
        Path inputPath = Paths.get(path);
        Path zipPath = Files.createTempFile("csar", ".zip");
        FileUtil.zip(inputPath, zipPath);
        archiveUploadService.upload(zipPath);
    }

    public void uploadCsar(String name, String version) throws IOException, CSARVersionAlreadyExistsException, ParsingException {
        uploadCsar(CSAR_SOURCE_PATH + name + "/" + version);
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
    }
}
