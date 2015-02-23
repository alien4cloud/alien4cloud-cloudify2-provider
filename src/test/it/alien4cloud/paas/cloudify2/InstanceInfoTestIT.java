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
public class InstanceInfoTestIT extends GenericTestCase {

    public InstanceInfoTestIT() {
    }

    @Test
    public void testScaleAndGetInstancesInformations() throws Exception {
        log.info("\n\n >> Executing Test testScaleAndGetInstancesInformations \n");
        String cloudifyAppId = null;
        this.uploadGitArchive("samples", "tomcat-war");
        this.uploadTestArchives("test-types-1.0-SNAPSHOT");

        String[] computes = new String[] { "comp_tomcat_scaling" };
        cloudifyAppId = deployTopology("compTomcatScaling", computes, null);
        Topology topo = alienDAO.findById(Topology.class, cloudifyAppId);
        Map<String, Map<String, InstanceInformation>> instancesInformations = cloudifyPaaSPovider.getInstancesInformation(cloudifyAppId, topo);
        printStatuses(instancesInformations);
        assertStartedInstance("comp_tomcat_scaling", 1, instancesInformations);
        assertAllInstanceStatus("comp_tomcat_scaling", InstanceStatus.SUCCESS, instancesInformations);
        assertAllInstanceStatus("tomcat", InstanceStatus.SUCCESS, instancesInformations);

        scale("comp_tomcat_scaling", 1, cloudifyAppId, topo, 10);

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
                sb.append("\t\tproperties=").append(map.getValue().getProperties()).append("\n");
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
