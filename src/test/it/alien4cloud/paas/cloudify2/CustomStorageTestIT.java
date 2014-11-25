package alien4cloud.paas.cloudify2;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.paas.plan.PlanGeneratorConstants;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-testit.xml")
@Slf4j
public class CustomStorageTestIT extends GenericStorageTestCase {

    @Resource(name = "cloudify-paas-provider-bean")
    protected CloudifyPaaSProvider anotherCloudifyPaaSPovider;

    public CustomStorageTestIT() {
    }

    @Test
    public void customBlockStorageVolumeIdProvidedSucessTest() throws Throwable {
        log.info("\n\n >> Executing Test customBlockStorageVolumeIdProvidedSucessTest \n");
        String cloudifyAppId = null;
        this.initElasticSearch(new String[] { "tosca-normative-types", "fastconnect-base-types", "deletable-storage-type", "custom-storage-types" },
                new String[] { "1.0.0-wd02-SNAPSHOT", "0.1.1", "0.1", "0.1-snapshot" });
        try {
            String[] computesId = new String[] { "custom_storage_volumeid" };
            cloudifyAppId = deployTopology("customBlockStorageWithVolumeId", computesId, true);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "custom_storage_volumeid", 1000L * 120);
            assertStorageEventFiredWithVolumeId(cloudifyAppId, new String[] { "blockstorage" }, PlanGeneratorConstants.STATE_CREATED);

        } catch (Exception e) {
            log.error("Test Failed", e);
            throw e;
        }
    }

    @Test
    // @Ignore
    public void customBlockStorageSizeProvidedSucessTest() throws Throwable {
        log.info("\n\n >> Executing Test customBlockStorageSizeProvidedSucessTest \n");
        String cloudifyAppId = null;
        this.initElasticSearch(new String[] { "tosca-normative-types", "fastconnect-base-types", "deletable-storage-type", "custom-storage-types" },
                new String[] { "1.0.0-wd02-SNAPSHOT", "0.1.1", "0.1", "0.1-snapshot" });
        try {

            String[] computesId = new String[] { "custom_storage_size" };
            cloudifyAppId = deployTopology("customDeletableBlockStorageWithSize", computesId, true);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "custom_storage_size", 1000L * 120);
            assertStorageEventFiredWithVolumeId(cloudifyAppId, new String[] { "blockstorage" }, PlanGeneratorConstants.STATE_CREATED);

        } catch (Exception e) {
            log.error("Test Failed", e);
            throw e;
        }
    }
}
