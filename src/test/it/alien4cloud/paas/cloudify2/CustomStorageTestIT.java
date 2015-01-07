package alien4cloud.paas.cloudify2;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-testit.xml")
@Slf4j
public class CustomStorageTestIT extends GenericStorageTestCase {

    @Resource(name = "cloudify-paas-provider-bean")
    protected CloudifyPaaSProvider anotherCloudifyPaaSPovider;

    public CustomStorageTestIT() {
    }

    @Test
    // @Ignore
    public void customBlockStorageSizeProvidedSucessTest() throws Throwable {
        log.info("\n\n >> Executing Test customBlockStorageSizeProvidedSucessTest \n");
        String cloudifyAppId = null;
        this.uploadGitArchive(EXTENDED_TYPES_REPO, EXTENDED_STORAGE_TYPES);
        this.uploadTestArchives("custom-storage-types-1.0-SNAPSHOT");
        try {

            String[] computesId = new String[] { "custom_storage_size" };
            cloudifyAppId = deployTopology("customDeletableBlockStorageWithSize", computesId, null);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "custom_storage_size", 1000L * 120);
            assertStorageEventFiredWithVolumeId(cloudifyAppId, new String[] { "blockstorage" }, ToscaNodeLifecycleConstants.CREATED);

        } catch (Exception e) {
            log.error("Test Failed", e);
            throw e;
        }
    }

    @Test
    // @Ignore
    public void configurableBlockStorageWithPropsProvidedSucessTest() throws Throwable {
        log.info("\n\n >> Executing Test configurableBlockStorageWithPropsProvidedSucessTest \n");
        String cloudifyAppId = null;
        this.uploadGitArchive(EXTENDED_TYPES_REPO, EXTENDED_STORAGE_TYPES);
        this.uploadTestArchives("custom-storage-types-1.0-SNAPSHOT");
        try {

            String[] computesId = new String[] { "config_storage_props" };
            cloudifyAppId = deployTopology("configurableBlockStorageWithPropsProvided", computesId, null);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "config_storage_props", 1000L * 120);
            assertStorageEventFiredWithVolumeId(cloudifyAppId, new String[] { "blockstorage" }, ToscaNodeLifecycleConstants.CREATED);

        } catch (Exception e) {
            log.error("Test Failed", e);
            throw e;
        }
    }
}
