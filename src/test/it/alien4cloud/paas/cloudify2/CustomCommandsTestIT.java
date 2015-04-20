package alien4cloud.paas.cloudify2;

import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.cloudifysource.restclient.exceptions.RestClientException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.google.common.collect.Maps;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-testit.xml")
@Slf4j
public class CustomCommandsTestIT extends GenericTestCase {

    @Resource(name = "cloudify-paas-provider-bean")
    protected CloudifyPaaSProvider anotherCloudifyPaaSPovider;

    public CustomCommandsTestIT() {
    }

    /**
     * Ensure that all interfaces are exposed as custom commands except (standard & fc extension).
     */
    @Test
    public void computeWitMultipleInterfaceCommandTest() throws Throwable {
        log.info("\n\n >> Executing Test computeWithBatchScriptsTest \n");

        String cloudifyAppId = null;
        this.uploadGitArchive("samples", "tomcat-war");
        this.uploadTestArchives("test-types-1.0-SNAPSHOT");
        try {
            String[] computesId = new String[] { "comp_custom_cmd" };
            cloudifyAppId = deployTopology("customCmd", computesId, null, null);
            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "comp_custom_cmd", 1000L * 120);
            testCustomCommandSuccess(cloudifyAppId, "comp_custom_cmd", null, "fr.fastconnect.custom", "bolo", null, null);
            Map<String, String> params = Maps.newHashMap();
            params.put("p1", "kikoo");
            testCustomCommandSuccess(cloudifyAppId, "comp_custom_cmd", null, "fr.fastconnect.custom", "bala", params, "B A L A : kikoo");
            // should fail since it's not really a custom command
            testCustomCommandFail(cloudifyAppId, "comp_custom_cmd", null, "fastconnect.cloudify.extensions", "start_detection", null);
            // should fail since it's not really a custom command
            testCustomCommandFail(cloudifyAppId, "comp_custom_cmd", null, "tosca.interfaces.node.lifecycle.Standard", "stop", null);
        } catch (Exception e) {
            log.error("Test Failed: " + (e instanceof RestClientException ? ((RestClientException) e).getMessageFormattedText() : e.getMessage()), e);
            throw e;
        }

    }

}
