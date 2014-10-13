package alien4cloud.paas.cloudify2.testutils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.Resource;

import org.springframework.stereotype.Component;

import alien4cloud.component.repository.exception.CSARVersionAlreadyExistsException;
import alien4cloud.tosca.container.archive.CsarUploadService;
import alien4cloud.tosca.container.exception.CSARParsingException;
import alien4cloud.tosca.container.exception.CSARValidationException;
import alien4cloud.utils.FileUtil;

@Component
public class ElasticSearchUtils {

    @Resource
    private CsarUploadService csarUploadService;

    public void uploadCSAR(String path) throws IOException, CSARParsingException, CSARVersionAlreadyExistsException, CSARValidationException {
        Path inputPath = Paths.get(path);
        Path zipPath = Files.createTempFile("csar", ".zip");
        FileUtil.zip(inputPath, zipPath);
        csarUploadService.uploadCsar(zipPath);
    }
}
