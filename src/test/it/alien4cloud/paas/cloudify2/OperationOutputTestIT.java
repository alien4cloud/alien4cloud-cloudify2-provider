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

    @Test
    public void testGetOperationOutputOnAttributes() throws Throwable {
        log.info("\n\n >> Executing Test testGetOperationOutputOnAttributes \n");
        this.uploadTestArchives("test-types-1.0-SNAPSHOT");
        String cloudifyAppId = null;

        String compTargetName = "comp_getOpOutputTarget";
        String compSourceName = "comp_getOpOutputSource";
        String[] computes = new String[] { compTargetName, compSourceName };
        cloudifyAppId = deployTopology("getOperationOutput", computes, null, null);
        Topology topo = alienDAO.findById(Topology.class, cloudifyAppId);
        Map<String, Map<String, InstanceInformation>> instancesInformations = cloudifyPaaSPovider.getInstancesInformation(cloudifyAppId);
        printStatuses(instancesInformations);

        testEvents(cloudifyAppId, new String[] { compTargetName, compSourceName }, 30000L, ToscaNodeLifecycleConstants.CREATED,
                ToscaNodeLifecycleConstants.CONFIGURED, ToscaNodeLifecycleConstants.STARTED, ToscaNodeLifecycleConstants.AVAILABLE);

        testRelationsEventsSucceeded(cloudifyAppId, null, lastRelIndex, 20000L, ToscaRelationshipLifecycleConstants.ADD_TARGET);

        assertStartedInstance(compTargetName, 1, instancesInformations);
        assertStartedInstance(compSourceName, 1, instancesInformations);
        assertAllInstanceStatus(compSourceName, InstanceStatus.SUCCESS, instancesInformations);
        assertAllInstanceStatus(compTargetName, InstanceStatus.SUCCESS, instancesInformations);

        Map<String, String> sourceAttributes = instancesInformations.get(compSourceName).get("1").getAttributes();
        Map<String, String> targetAttributes = instancesInformations.get(compTargetName).get("1").getAttributes();

        Assert.assertNotNull(sourceAttributes);
        Assert.assertNotNull(targetAttributes);

        Assert.assertEquals("concat/thisIsATestForConcat", sourceAttributes.get("concat_attribute"));
        Assert.assertEquals("concat/thisIsATestForConcat", targetAttributes.get("concat_attribute"));

        // TODO: test scaling
    }

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

    private void assertStartedInstance(String nodeId, int expectedInstances, Map<String, Map<String, InstanceInformation>> instancesInformations) {
        Map<String, InstanceInformation> nodeInstancesInfos = instancesInformations.get(nodeId);
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
