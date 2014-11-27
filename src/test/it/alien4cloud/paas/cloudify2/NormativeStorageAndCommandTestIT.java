package alien4cloud.paas.cloudify2;

import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.HashedMap;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.paas.plan.PlanGeneratorConstants;

import com.google.common.collect.Lists;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-testit.xml")
@Slf4j
public class StorageAndCommandTestIT extends GenericStorageTestCase {

    @Resource(name = "cloudify-paas-provider-bean")
    protected CloudifyPaaSProvider anotherCloudifyPaaSPovider;

    public StorageAndCommandTestIT() {
    }

    @Test
    public void customCommandTest() throws Exception {
        log.info("\n\n >> Executing Test customCommandTest \n");
        String cloudifyAppId = null;
        this.initElasticSearch(new String[] { "tosca-base-types", "fastconnect-base-types", "tomcat-test-types" },
                new String[] { "1.0", "0.1", "0.2-snapshot" });
        try {
            String[] computesId = new String[] { "serveur_web" };
            cloudifyAppId = deployTopology("customCmd", computesId, true);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "serveur_web", 1000L * 120);
            assertHttpCodeEquals(cloudifyAppId, "serveur_web", "8080", "", HTTP_CODE_OK, null);

            testCustomCommandSuccess(cloudifyAppId, "tomcat", null, "updateWar", Lists.newArrayList("helloWorld2.war"), null);
            testCustomCommandFail(cloudifyAppId, "tomcat", null, "updateWar", null);
            testCustomCommandSuccess(cloudifyAppId, "tomcat", 1, "updateWar", Lists.newArrayList("helloWorld2.war"), null);
            testCustomCommandFail(cloudifyAppId, "tomcat", 1, "updateWar", Lists.newArrayList("fakeHelloWorld2.war"));

            // testEvents(applicationId, new String[] { "ComputeTomcat", "Tomcat" }, PlanGeneratorConstants.STATE_STOPPING,
            // PlanGeneratorConstants.STATE_STOPPED);

        } catch (Exception e) {
            log.error("Test Failed", e);
            throw e;
        }
    }

    @Test
    public void blockStorageVolumeIdProvidedSucessTest() throws Throwable {
        log.info("\n\n >> Executing Test blockStorageVolumeIdProvidedSucessTest \n");
        String cloudifyAppId = null;
        this.initElasticSearch(new String[] { "tosca-normative-types", "fastconnect-base-types" }, new String[] { "1.0.0-wd02-SNAPSHOT", "0.1.1" });
        try {
            String[] computesId = new String[] { "comp_storage_volumeid" };
            cloudifyAppId = deployTopology("blockStorageWithVolumeId", computesId, true);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "comp_storage_volumeid", 1000L * 120);
            assertStorageEventFiredWithVolumeId(cloudifyAppId, new String[] { "blockstorage" }, PlanGeneratorConstants.STATE_CREATED);

        } catch (Exception e) {
            log.error("Test Failed", e);
            throw e;
        }
    }

    @Test
    // @Ignore
    public void blockStorageSizeProvidedSucessTest() throws Throwable {
        log.info("\n\n >> Executing Test blockStorageSizeProvidedSucessTest \n");
        String cloudifyAppId = null;
        this.initElasticSearch(new String[] { "tosca-normative-types", "fastconnect-base-types", "deletable-storage-type" }, new String[] {
                "1.0.0-wd02-SNAPSHOT", "0.1.1", "0.1" });
        try {

            String[] computesId = new String[] { "comp_storage_size" };
            cloudifyAppId = deployTopology("deletableBlockStorageWithSize", computesId, true);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "comp_storage_size", 1000L * 120);
            assertStorageEventFiredWithVolumeId(cloudifyAppId, new String[] { "blockstorage" }, PlanGeneratorConstants.STATE_CREATED);

        } catch (Exception e) {
            log.error("Test Failed", e);
            throw e;
        }
    }

    private void testCustomCommandFail(String applicationId, String nodeName, Integer instanceId, String command, List<String> params) {
        boolean fail = false;
        try {
            executeCustomCommand(applicationId, nodeName, instanceId, command, params);
        } catch (OperationExecutionException e) {
            fail = true;
        } finally {
            assertTrue(fail);
        }
    }

    private void testCustomCommandSuccess(String cloudifyAppId, String nodeName, Integer instanceId, String command, List<String> params,
            String expectedResultSnippet) {
        Map<String, String> result = executeCustomCommand(cloudifyAppId, nodeName, instanceId, command, params);

        if (expectedResultSnippet != null) {
            for (String opReslt : result.values()) {
                Assert.assertTrue("Command result should have contain <" + expectedResultSnippet + ">",
                        opReslt.toLowerCase().contains(expectedResultSnippet.toLowerCase()));
            }
        }
    }

    private Map<String, String> executeCustomCommand(String cloudifyAppId, String nodeName, Integer instanceId, String command, List<String> params) {
        if (!deployedCloudifyAppIds.contains(cloudifyAppId)) {
            Assert.fail("Topology not found in deployments");
        }

        NodeOperationExecRequest request = new NodeOperationExecRequest();
        request.setInterfaceName("custom");
        request.setOperationName(command);
        request.setNodeTemplateName(nodeName);

        if (instanceId != null) {
            request.setInstanceId(instanceId.toString());
        }

        // request.setCloudId(topo.getCloudId());
        Map<String, String> paramss = new HashedMap<>();
        if (CollectionUtils.isNotEmpty(params)) {
            for (String param : params) {
                paramss.put("key-" + paramss.size(), param);
            }
            request.setParameters(paramss);
        }

        Map<String, String> result = cloudifyPaaSPovider.executeOperation(cloudifyAppId, request);

        log.info("Test result is: \n\t" + result);
        return result;
    }

}
