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
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;

import com.google.common.collect.Lists;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-testit.xml")
@Slf4j
public class NormativeStorageAndCommandTestIT extends GenericStorageTestCase {

    @Resource(name = "cloudify-paas-provider-bean")
    protected CloudifyPaaSProvider anotherCloudifyPaaSPovider;

    public NormativeStorageAndCommandTestIT() {
    }

    @Test
    public void customCommandTest() throws Exception {
        log.info("\n\n >> Executing Test customCommandTest \n");
        String cloudifyAppId = null;
        this.initElasticSearch(new String[] { "tomcat-test-types" }, new String[] { "1.0-SNAPSHOT" });
        try {
            String[] computesId = new String[] { "comp_custom_cmd" };
            cloudifyAppId = deployTopology("customCmd", computesId);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "comp_custom_cmd", 1000L * 120);

            String resultSnipet = "hello <alien>, os_version is <ubuntu>, from <comp_custom_cmd";
            String resultSnipetInst = "hello <alien>, os_version is <ubuntu>, from <comp_custom_cmd.1>";

            testCustomCommandSuccess(cloudifyAppId, "tomcat", null, "helloCmd", Lists.newArrayList("alien"), resultSnipet);
            testCustomCommandFail(cloudifyAppId, "tomcat", null, "helloCmd", null);
            testCustomCommandSuccess(cloudifyAppId, "tomcat", 1, "helloCmd", Lists.newArrayList("alien"), resultSnipetInst);
            testCustomCommandFail(cloudifyAppId, "tomcat", 1, "helloCmd", Lists.newArrayList("failThis"));

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
            cloudifyAppId = deployTopology("blockStorageWithVolumeId", computesId);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "comp_storage_volumeid", 1000L * 120);
            assertStorageEventFiredWithVolumeId(cloudifyAppId, new String[] { "blockstorage" }, ToscaNodeLifecycleConstants.CREATED);

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
        this.initElasticSearch(new String[] { "deletable-storage-type" }, new String[] { "1.0" });
        try {

            String[] computesId = new String[] { "comp_storage_size" };
            cloudifyAppId = deployTopology("deletableBlockStorageWithSize", computesId);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "comp_storage_size", 1000L * 120);
            assertStorageEventFiredWithVolumeId(cloudifyAppId, new String[] { "blockstorage" }, ToscaNodeLifecycleConstants.CREATED);

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
                Assert.assertTrue("Command result is <" + opReslt.toLowerCase() + ">. It should have contain <" + expectedResultSnippet + ">", opReslt
                        .toLowerCase().contains(expectedResultSnippet.toLowerCase()));
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
