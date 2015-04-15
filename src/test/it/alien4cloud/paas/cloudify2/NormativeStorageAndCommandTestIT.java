package alien4cloud.paas.cloudify2;

import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;

import com.google.common.collect.Maps;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-testit.xml")
@Slf4j
public class NormativeStorageAndCommandTestIT extends GenericStorageTestCase {

    @Resource(name = "cloudify-paas-provider-bean")
    protected CloudifyPaaSProvider anotherCloudifyPaaSPovider;

    private static Map<String, String> providerDeploymentProperties;

    static {
        // define provider deployment properties
        providerDeploymentProperties = Maps.newHashMap();
        providerDeploymentProperties.put(DeploymentPropertiesNames.DELETABLE_BLOCKSTORAGE, "true");
    }

    public NormativeStorageAndCommandTestIT() {
    }

    private void setDeletableBlockStorage(String isDeletable) {
        providerDeploymentProperties.put(DeploymentPropertiesNames.DELETABLE_BLOCKSTORAGE, Boolean.valueOf(isDeletable).toString());
    }

    @Test
    public void customCommandTest() throws Throwable {
        log.info("\n\n >> Executing Test customCommandTest \n");
        String cloudifyAppId = null;
        this.uploadGitArchive("samples", "tomcat-war");
        this.uploadTestArchives("test-types-1.0-SNAPSHOT");
        try {
            String[] computesId = new String[] { "comp_custom_cmd" };
            cloudifyAppId = deployTopology("customCmd", computesId, null, null);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "comp_custom_cmd", 1000L * 120);

            String resultSnipet = "hello <alien>, customHostName is <testCompute>, from <comp_custom_cmd";
            String resultSnipetInst = "hello <alien>, customHostName is <testCompute>, from <comp_custom_cmd.1>";
            Map<String, String> params = Maps.newHashMap();
            params.put("yourName", "alien");
            testCustomCommandSuccess(cloudifyAppId, "comp_custom_cmd", null, "helloCmd", params, resultSnipet);
            params.put("yourName", null);
            testCustomCommandFail(cloudifyAppId, "comp_custom_cmd", null, "helloCmd", null);
            params.put("yourName", "alien");
            testCustomCommandSuccess(cloudifyAppId, "comp_custom_cmd", 1, "helloCmd", params, resultSnipetInst);
            params.put("yourName", "failThis");
            testCustomCommandFail(cloudifyAppId, "comp_custom_cmd", 1, "helloCmd", params);

        } catch (Exception e) {
            log.error("Test Failed", e);
            throw e;
        }
    }

    @Test
    public void blockStorageVolumeIdProvidedSucessTest() throws Throwable {
        log.info("\n\n >> Executing Test blockStorageVolumeIdProvidedSucessTest \n");
        String cloudifyAppId = null;
        try {
            String[] computesId = new String[] { "comp_storage_volumeid" };
            cloudifyAppId = deployTopology("blockStorageWithVolumeId", computesId, null, providerDeploymentProperties);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "comp_storage_volumeid", 1000L * 120);
            assertStorageEventFiredWithVolumeId(cloudifyAppId, new String[] { "blockstorage" }, ToscaNodeLifecycleConstants.CREATED);

        } catch (Exception e) {
            log.error("Test Failed", e);
            throw e;
        }
    }

    @Test
    public void blockStorageSizeProvidedSucessTest() throws Throwable {
        log.info("\n\n >> Executing Test blockStorageSizeProvidedSucessTest \n");
        String cloudifyAppId = null;
        this.uploadGitArchive(EXTENDED_TYPES_REPO, EXTENDED_STORAGE_TYPES);
        try {

            String[] computesId = new String[] { "comp_storage_size" };
            cloudifyAppId = deployTopology("deletableBlockStorageWithSize", computesId, null, providerDeploymentProperties);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "comp_storage_size", 1000L * 120);
            assertStorageEventFiredWithVolumeId(cloudifyAppId, new String[] { "blockstorage" }, ToscaNodeLifecycleConstants.CREATED);

        } catch (Exception e) {
            log.error("Test Failed", e);
            throw e;
        }
    }

    @Test
    public void blockStorageWithDeletabeBSDeploymentPropertyTest() throws Throwable {

        log.info("\n\n >> Executing Test blockStorageWithDeletableBlockStorageOptionTest \n");
        String cloudifyAppId = null;
        this.uploadGitArchive(EXTENDED_TYPES_REPO, EXTENDED_STORAGE_TYPES);

        try {

            String[] computesId = new String[] { "comp_storage_size" };
            cloudifyAppId = deployTopology("deletableBlockStorageWithSize", computesId, null, providerDeploymentProperties);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "comp_storage_size", 1000L * 120);
            // a blockstorage event of type "CREATED" should exist
            assertStorageEventFiredWithVolumeId(cloudifyAppId, new String[] { "blockstorage" }, ToscaNodeLifecycleConstants.CREATED);
            testUndeployment(cloudifyAppId);
            // check that the blockstorage is stopped / deleted
            assertBlockStorageEventFired(cloudifyAppId, "blockstorage", ToscaNodeLifecycleConstants.STOPPED);

        } catch (Exception e) {
            log.error("Test Failed", e);
            throw e;
        }
    }

}
