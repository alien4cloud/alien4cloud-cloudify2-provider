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

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-testit.xml")
@Slf4j
public class OperationOutputTestIT extends GenericTestCase {

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

        String[] computes = new String[] { "comp_getOpOutput" };
        cloudifyAppId = deployTopology("getOperationOutput", computes, null, null);
        Topology topo = alienDAO.findById(Topology.class, cloudifyAppId);
        Map<String, Map<String, InstanceInformation>> instancesInformations = cloudifyPaaSPovider.getInstancesInformation(cloudifyAppId);
        printStatuses(instancesInformations);
        assertStartedInstance("comp_getOpOutput", 1, instancesInformations);
        assertAllInstanceStatus("comp_getOpOutput", InstanceStatus.SUCCESS, instancesInformations);

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
