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
    public void customCommandTest() throws Throwable {
        log.info("\n\n >> Executing Test customCommandTest \n");

        String cloudifyAppId = null;
        this.uploadGitArchive("samples", "tomcat-war");
        this.uploadTestArchives("test-types-1.0-SNAPSHOT");
        try {
            String computeName = "comp_custom_cmd";
            String[] computesId = new String[] { computeName };
            cloudifyAppId = deployTopology("customCmd", computesId, null, null);
            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, computeName, 1000L * 120);

            // test on custom interface
            String resultSnipet = "hello <alien>, customHostName is <testCompute>, from instance <";
            String resultSnipetInst = "hello <alien>, customHostName is <testCompute>, from instance <1>";
            Map<String, String> params = Maps.newHashMap();

            params.put("yourName", "alien");
            // test on service level
            testCustomCommandSuccess(cloudifyAppId, computeName, null, "custom", "helloCmd", params, resultSnipet);
            params.put("yourName", null);
            // should fail since param is null
            testCustomCommandFail(cloudifyAppId, computeName, null, "custom", "helloCmd", null);
            params.put("yourName", "alien");
            // test on instance level
            testCustomCommandSuccess(cloudifyAppId, computeName, 1, "custom", "helloCmd", params, resultSnipetInst);
            params.put("yourName", "failThis");
            // should fail based on the value of the param
            testCustomCommandFail(cloudifyAppId, computeName, 1, "custom", "helloCmd", params);

            // test other interfaces
            testCustomCommandSuccess(cloudifyAppId, computeName, null, "fr.fastconnect.custom", "bolo", null, null);
            params.clear();
            params.put("p1", "kikoo");
            testCustomCommandSuccess(cloudifyAppId, computeName, null, "fr.fastconnect.custom", "bala", params, "B A L A : kikoo");
            // should fail since it's not really a custom command
            testCustomCommandFail(cloudifyAppId, computeName, null, "fastconnect.cloudify.extensions", "start_detection", null);
            // should fail since it's not really a custom command
            testCustomCommandFail(cloudifyAppId, computeName, null, "tosca.interfaces.node.lifecycle.Standard", "stop", null);
            // test that a bash script can return value
            testCustomCommandSuccess(cloudifyAppId, computeName, null, "fr.fastconnect.custom", "bashWithOuput", params,
                    "Here is my command output from stdout");

        } catch (Exception e) {
            log.error("Test Failed: " + (e instanceof RestClientException ? ((RestClientException) e).getMessageFormattedText() : e.getMessage()), e);
            throw e;
        }

    }

}
