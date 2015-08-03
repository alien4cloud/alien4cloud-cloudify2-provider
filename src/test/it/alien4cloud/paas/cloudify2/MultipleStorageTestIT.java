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
public class MultipleStorageTestIT extends GenericStorageTestCase {

    @Resource(name = "cloudify-paas-provider-bean")
    protected CloudifyPaaSProvider anotherCloudifyPaaSPovider;

    private static Map<String, String> providerDeploymentProperties;

    static {
        // define provider deployment properties
        providerDeploymentProperties = Maps.newHashMap();
        providerDeploymentProperties.put(DeploymentPropertiesNames.DELETABLE_BLOCKSTORAGE, "true");
    }

    public MultipleStorageTestIT() {
    }

    private void setDeletableBlockStorage(String isDeletable) {
        providerDeploymentProperties.put(DeploymentPropertiesNames.DELETABLE_BLOCKSTORAGE, Boolean.valueOf(isDeletable).toString());
    }

    @Test
    public void blockStorageVolumeIdProvidedSucessTest() throws Throwable {
        log.info("\n\n >> Executing Test blockStorageVolumeIdProvidedSucessTest \n");
        // this.uploadGitArchive(EXTENDED_TYPES_REPO, EXTENDED_STORAGE_TYPES);
        String cloudifyAppId = null;
        try {
            String[] computesId = new String[] { "comp_multiStorage" };
            cloudifyAppId = deployTopology("multiStorage", computesId, null, providerDeploymentProperties);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "comp_multiStorage", 1000L * 120);
            assertStorageEventFiredWithVolumeId(cloudifyAppId, new String[] { "blockstorage", "blockstorage2", "blockstorage3" },
                    ToscaNodeLifecycleConstants.CREATED);

        } catch (Exception e) {
            log.error("Test Failed", e);
            throw e;
        }
    }

}
