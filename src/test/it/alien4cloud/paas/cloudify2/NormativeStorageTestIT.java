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
public class NormativeStorageTestIT extends GenericStorageTestCase {

    @Resource(name = "cloudify-paas-provider-bean")
    protected CloudifyPaaSProvider anotherCloudifyPaaSPovider;

    private static Map<String, String> providerDeploymentProperties;

    static {
        // define provider deployment properties
        providerDeploymentProperties = Maps.newHashMap();
        providerDeploymentProperties.put(DeploymentPropertiesNames.DELETABLE_BLOCKSTORAGE, "true");
    }

    public NormativeStorageTestIT() {
    }

    private void setDeletableBlockStorage(String isDeletable) {
        providerDeploymentProperties.put(DeploymentPropertiesNames.DELETABLE_BLOCKSTORAGE, Boolean.valueOf(isDeletable).toString());
    }

    // Tests done in CustomCommandsTestIT
    @Test
    public void blockStorageVolumeIdProvidedSucessTest() throws Throwable {
        log.info("\n\n >> Executing Test blockStorageVolumeIdProvidedSucessTest \n");
        setDeletableBlockStorage("false");
        this.uploadGitArchive(EXTENDED_TYPES_REPO, "", EXTENDED_STORAGE_TYPES);
        this.uploadTestArchives("custom-storage-types-1.0-SNAPSHOT");
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
        this.uploadGitArchive(EXTENDED_TYPES_REPO, "1.0.0", EXTENDED_STORAGE_TYPES);
        try {

            String[] computesId = new String[] { "comp_storage_size" };
            cloudifyAppId = deployTopology("deletableBlockStorageWithSize", computesId, null, providerDeploymentProperties);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "comp_storage_size", 1000L * 120);
            assertStorageEventFiredWithVolumeId(cloudifyAppId, new String[] { "blockstorage" }, ToscaNodeLifecycleConstants.CREATED);

            // testUndeployment(cloudifyAppId);
            // // check that the blockstorage is stopped / deleted
            // assertBlockStorageEventFired(cloudifyAppId, "blockstorage", ToscaNodeLifecycleConstants.STOPPED);

        } catch (Exception e) {
            log.error("Test Failed", e);
            throw e;
        }
    }

}
