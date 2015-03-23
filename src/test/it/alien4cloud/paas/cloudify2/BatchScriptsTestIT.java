package alien4cloud.paas.cloudify2;

import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.model.cloud.ComputeTemplate;

import com.google.common.collect.Maps;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-testit.xml")
@Slf4j
public class BatchScriptsTestIT extends GenericTestCase {

    @Resource(name = "cloudify-paas-provider-bean")
    protected CloudifyPaaSProvider anotherCloudifyPaaSPovider;

    public BatchScriptsTestIT() {
    }

    @Override
    public void after() {
        // TODO Auto-generated method stub
        // super.after();
    }

    @Test
    @Ignore
    public void computeWithBatchScriptsTest() throws Throwable {
        log.info("\n\n >> Executing Test computeWithBatchScriptsTest \n");

        this.uploadGitArchive("samples", "tomcat-war");
        this.uploadTestArchives("test-types-1.0-SNAPSHOT");
        Map<String, ComputeTemplate> templates = Maps.newHashMap();
        templates.put("computeWindows", new ComputeTemplate(ALIEN_WINDOWS_IMAGE, ALIEN_FLAVOR));
        String cloudifyAppId = deployTopology("computeWindows", null, templates);
        this.assertApplicationIsInstalled(cloudifyAppId);
        waitForServiceToStarts(cloudifyAppId, "computewindows", 1000L * 120);

        Map<String, String> params = Maps.newHashMap();
        params.put("filename", "created.txt");
        testCustomCommandSuccess(cloudifyAppId, "computeWindows", null, "checkFile", params, null);

        params.put("filename", "configured.txt");
        testCustomCommandSuccess(cloudifyAppId, "computeWindows", null, "checkFile", params, null);
    }

}
