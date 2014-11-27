package alien4cloud.paas.cloudify2.testutils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import alien4cloud.component.repository.exception.CSARVersionAlreadyExistsException;
import alien4cloud.tosca.ArchiveUploadService;
import alien4cloud.tosca.parser.ParsingException;
import alien4cloud.utils.FileUtil;

@Component
public class ElasticSearchUtils {

    @Resource
    private ArchiveUploadService archiveUploadService;

    public void uploadCSAR(String path) throws IOException, ParsingException, CSARVersionAlreadyExistsException {
        Path inputPath = Paths.get(path);
        Path zipPath = Files.createTempFile("csar", ".zip");
        FileUtil.zip(inputPath, zipPath);
        archiveUploadService.upload(zipPath);
    }
}
