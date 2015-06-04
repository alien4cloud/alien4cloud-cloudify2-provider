package alien4cloud.paas.cloudify2;

import java.util.Map;
import java.util.Map.Entry;

import lombok.extern.slf4j.Slf4j;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.model.topology.Topology;
import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.InstanceStatus;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.paas.plan.ToscaRelationshipLifecycleConstants;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-testit.xml")
@Slf4j
public class OperationOutputTestIT extends GenericRelationshipTriggeringTestCase {

    public OperationOutputTestIT() {
    }

    @Override
    public void after() {
        // TODO Auto-generated method stub
        // super.after();
    }

    @Test
    public void testGetOperationOutputOnAttributes() throws Throwable {
        log.info("\n\n >> Executing Test testGetOperationOutputOnAttributes \n");
        this.uploadTestArchives("test-types-1.0-SNAPSHOT");
        String cloudifyAppId = null;

        String[] computes = new String[] { "comp_getOpOutputTarget", "comp_getOpOutputSource" };
        cloudifyAppId = deployTopology("getOperationOutput", computes, null, null);
        Topology topo = alienDAO.findById(Topology.class, cloudifyAppId);
        Map<String, Map<String, InstanceInformation>> instancesInformations = cloudifyPaaSPovider.getInstancesInformation(cloudifyAppId);
        printStatuses(instancesInformations);

        testEvents(cloudifyAppId, new String[] { "comp_getOpOutputTarget", "comp_getOpOutputSource" }, 30000L, ToscaNodeLifecycleConstants.CREATED,
                ToscaNodeLifecycleConstants.CONFIGURED, ToscaNodeLifecycleConstants.STARTED, ToscaNodeLifecycleConstants.AVAILABLE);

        testRelationsEventsSucceeded(cloudifyAppId, null, lastRelIndex, 20000L, ToscaRelationshipLifecycleConstants.ADD_TARGET);

        assertStartedInstance("comp_getOpOutputTarget", 1, instancesInformations);
        assertStartedInstance("comp_getOpOutputSource", 1, instancesInformations);
        assertAllInstanceStatus("comp_getOpOutputSource", InstanceStatus.SUCCESS, instancesInformations);
        assertAllInstanceStatus("comp_getOpOutputTarget", InstanceStatus.SUCCESS, instancesInformations);

        Map<String, String> outputs = this.cloudifyRestClientManager.getRestClient().getOperationOutputs(cloudifyAppId, "comp_getopoutputsource", "1");
        System.out.println("OUTPUTS ==> \n" + outputs);

        // TODO: test scaling
    }

    // @Test
    // @Ignore
    // public void test2() throws Throwable {
    // log.info("\n\n >> Executing Test testGetOperationOutputOnAttributes \n");
    // String cloudifyAppId = "e83d61af-e3bf-4162-958c-76674cc25fd3";
    // String[] computes = new String[] { "comp_getOpOutput" };
    // CloudifyRestClient restClient = this.cloudifyRestClientManager.getRestClient();
    // Map<String, String> outputs = null;
    // try {
    // System.out.println(restClient.getAllInstancesOperationsOutputs(cloudifyAppId, "comp_getopoutput"));
    // outputs = restClient.getOperationOutputs(cloudifyAppId, "comp_getopoutput", "1");
    // } catch (RestClientException e) {
    // log.error(e.getMessageFormattedText());
    // log.error(e.getVerbose(), e);
    // // e.printStackTrace();
    // }
    // System.out.println("OUTPUTS ==> \n" + outputs);
    //
    // // TODO: test scaling
    // }

    private void printStatuses(Map<String, Map<String, InstanceInformation>> instancesInformations) {
        StringBuilder sb = new StringBuilder("\n");
        for (Entry<String, Map<String, InstanceInformation>> entry : instancesInformations.entrySet()) {
            sb.append(entry.getKey()).append("\n");
            for (Entry<String, InstanceInformation> map : entry.getValue().entrySet()) {
                sb.append("\t").append(map.getKey()).append(":\n");
                sb.append("\t\tstatus=").append(map.getValue().getState()).append("\n");
                sb.append("\t\tplanStatus=").append(map.getValue().getInstanceStatus()).append("\n");
                sb.append("\t\tattributes=").append(map.getValue().getAttributes()).append("\n");
                sb.append("\t\truntimeInfo=").append(map.getValue().getRuntimeProperties()).append("\n");
                sb.append("\t\toutputs=").append(map.getValue().getOperationsOutputs()).append("\n");
            }
        }

        log.info(sb.toString());
    }

    private void assertStartedInstance(String nodeID, int expectedInstances, Map<String, Map<String, InstanceInformation>> instancesInformations) {
        Map<String, InstanceInformation> nodeInstancesInfos = instancesInformations.get(nodeID);
        int started = 0;
        for (InstanceInformation instanceInfo : nodeInstancesInfos.values()) {
            if (instanceInfo.getInstanceStatus().equals(InstanceStatus.SUCCESS)) {
                started++;
            }
        }
        Assert.assertEquals(expectedInstances, started);
    }

    private void assertAllInstanceStatus(String nodeID, InstanceStatus status, Map<String, Map<String, InstanceInformation>> instancesInformations) {
        Map<String, InstanceInformation> instancesInfos = instancesInformations.get(nodeID);
        for (InstanceInformation instanceInfo : instancesInfos.values()) {
            Assert.assertEquals(status, instanceInfo.getInstanceStatus());
        }
    }
}
