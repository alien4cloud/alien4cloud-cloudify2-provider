package alien4cloud.paas.cloudify2;

import java.util.Map;
import java.util.Map.Entry;

import lombok.extern.slf4j.Slf4j;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.paas.model.InstanceInformation;
import alien4cloud.paas.model.InstanceStatus;
import alien4cloud.tosca.container.model.topology.Topology;

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
        this.initElasticSearch(new String[] { "tosca-base-types", "fastconnect-base-types", "tomcat-test-types" },
                new String[] { "1.0", "0.1", "0.2-snapshot" });

        String[] computes = new String[] { "serveur_web" };
        cloudifyAppId = deployTopology("compTomcatScaling", computes, true);
        Topology topo = alienDAO.findById(Topology.class, cloudifyAppId);
        Map<String, Map<Integer, InstanceInformation>> instancesInformations = cloudifyPaaSPovider.getInstancesInformation(cloudifyAppId, topo);
        printStatuses(instancesInformations);
        assertStartedInstance("serveur_web", 1, instancesInformations);
        assertAllInstanceStatus("serveur_web", InstanceStatus.SUCCESS, instancesInformations);
        assertAllInstanceStatus("tomcat", InstanceStatus.SUCCESS, instancesInformations);

        scale("serveur_web", 1, cloudifyAppId, topo);

        // TODO: test scaling
    }

    private void scale(String nodeID, int nbToAdd, String appId, Topology topo) {
        int plannedInstance = topo.getScalingPolicies().get(nodeID).getInitialInstances() + nbToAdd;
        log.info("Scaling to " + nbToAdd);
        topo.getScalingPolicies().get(nodeID).setInitialInstances(plannedInstance);
        alienDAO.save(topo);
        cloudifyPaaSPovider.scale(appId, nodeID, nbToAdd);
    }

    private void printStatuses(Map<String, Map<Integer, InstanceInformation>> instancesInformations) {
        StringBuilder sb = new StringBuilder("\n");
        for (Entry<String, Map<Integer, InstanceInformation>> entry : instancesInformations.entrySet()) {
            sb.append(entry.getKey()).append("\n");
            for (Entry<Integer, InstanceInformation> map : entry.getValue().entrySet()) {
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

    private void assertStartedInstance(String nodeID, int expectedInstances, Map<String, Map<Integer, InstanceInformation>> instancesInformations) {
        Map<Integer, InstanceInformation> nodeInstancesInfos = instancesInformations.get(nodeID);
        int started = 0;
        for (InstanceInformation instanceInfo : nodeInstancesInfos.values()) {
            if (instanceInfo.getInstanceStatus().equals(InstanceStatus.SUCCESS)) {
                started++;
            }
        }
        Assert.assertEquals(expectedInstances, started);
    }

    private void assertAllInstanceStatus(String nodeID, InstanceStatus status, Map<String, Map<Integer, InstanceInformation>> instancesInformations) {
        Map<Integer, InstanceInformation> instancesInfos = instancesInformations.get(nodeID);
        for (InstanceInformation instanceInfo : instancesInfos.values()) {
            Assert.assertEquals(status, instanceInfo.getInstanceStatus());
        }
    }
}
