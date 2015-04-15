package alien4cloud.paas.cloudify2;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.model.deployment.Deployment;
import alien4cloud.paas.cloudify2.events.NodeInstanceState;
import alien4cloud.paas.cloudify2.rest.CloudifyEventsListener;
import alien4cloud.paas.model.InstanceStatus;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;

import com.google.common.collect.Lists;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-testit.xml")
@Slf4j
public class MaintenanceModeTestIT extends GenericTestCase {

    public MaintenanceModeTestIT() {
    }

    @Override
    public void after() {
        // TODO Auto-generated method stub
        super.after();
    }

    @Test
    public void maintenanceModeTest() throws Throwable {
        log.info("\n\n >> Executing Test maintenanceModeTest \n");
        String cloudifyAppId = null;
        this.uploadGitArchive("samples", "tomcat-war");

        // String startMaintenanceResultSnipet = "agent failure detection disabled successfully for a period of";
        // String stopMaintenanceResultSnipet = "agent failure detection enabled successfully";
        try {
            String[] computesId = new String[] { "comp_maint_mode" };
            cloudifyAppId = deployTopology("compMaintenanceMode", computesId, null, null);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "comp_maint_mode", 1000L * 120);
            waitForServiceToStarts(cloudifyAppId, "comp_maint_mode_2", 1000L * 120);

            /** one instance of a node **/
            // ON
            testMaintenanceModeSuccess(cloudifyAppId, "comp_maint_mode", 1, true, Lists.newArrayList("tomcat"));
            assertInstanceStateCorrect(cloudifyAppId, "comp_maint_mode_2", null, ToscaNodeLifecycleConstants.AVAILABLE, null);

            // OFF
            testMaintenanceModeSuccess(cloudifyAppId, "comp_maint_mode", 1, false, Lists.newArrayList("tomcat"));
            assertInstanceStateCorrect(cloudifyAppId, "comp_maint_mode_2", null, ToscaNodeLifecycleConstants.AVAILABLE, null);

            /** all instances of a node **/
            // ON
            testMaintenanceModeSuccess(cloudifyAppId, "comp_maint_mode", null, true, Lists.newArrayList("tomcat"));
            assertInstanceStateCorrect(cloudifyAppId, "comp_maint_mode_2", null, ToscaNodeLifecycleConstants.AVAILABLE, null);

            // OFF
            testMaintenanceModeSuccess(cloudifyAppId, "comp_maint_mode", null, false, Lists.newArrayList("tomcat"));
            assertInstanceStateCorrect(cloudifyAppId, "comp_maint_mode_2", null, ToscaNodeLifecycleConstants.AVAILABLE, null);

            /** all nodes **/
            testMaintenanceModeSuccess(cloudifyAppId, null, null, true, Lists.newArrayList("tomcat"));

        } catch (Exception e) {
            log.error("Test Failed", e);
            throw e;
        }
    }

    protected void testMaintenanceModeSuccess(final String cloudifyAppId, final String nodeName, final Integer instanceId, final boolean maintenanceModeOn,
            List<String> children) {
        String instanceIdStr = instanceId != null ? instanceId.toString() : null;
        switchMaintenanceMode(cloudifyAppId, nodeName, instanceIdStr, maintenanceModeOn);

        String stateToCheck = maintenanceModeOn ? InstanceStatus.MAINTENANCE.toString().toLowerCase() : ToscaNodeLifecycleConstants.AVAILABLE;
        assertInstanceStateCorrect(cloudifyAppId, nodeName, instanceIdStr, stateToCheck, children);
    }

    private void assertInstanceStateCorrect(String applicationId, String nodeName, String instanceId, String expected, List<String> children) {
        List<NodeInstanceState> instanceStates = Lists.newArrayList();
        List<NodeInstanceState> toCheck = Lists.newArrayList();
        List<String> nodesNames = Lists.newArrayList();
        try {
            CloudifyEventsListener listener = new CloudifyEventsListener(cloudifyRestClientManager.getRestEventEndpoint(), "", "");
            instanceStates = listener.getNodeInstanceStates(applicationId);
        } catch (Exception e) {
            Assert.fail("error when trying to get instance states to check: " + e.getMessage());
        }
        Assert.assertFalse(instanceStates.isEmpty());
        if (children != null) {
            nodesNames.addAll(children);
        }
        if (StringUtils.isBlank(nodeName)) {
            nodesNames.add(nodeName);
        }

        for (NodeInstanceState instance : instanceStates) {
            if (nodesNames.isEmpty()
                    || (nodesNames.contains(instance.getNodeTemplateId()) && (StringUtils.isBlank(instanceId) || instance.getInstanceId().equals(instanceId)))) {
                toCheck.add(instance);
            }
        }
        for (NodeInstanceState check : toCheck) {
            Assert.assertEquals("State of node " + check.getNodeTemplateId() + ", instance " + check.getInstanceId() + " should be: " + expected, expected,
                    check.getInstanceState());
        }
    }

    protected void switchMaintenanceMode(String cloudifyAppId, String nodeName, String instanceId, boolean on) {
        if (!deployedCloudifyAppIds.contains(cloudifyAppId)) {
            Assert.fail("Topology not found in deployments");
        }
        PaaSTopologyDeploymentContext deploymentContext = new PaaSTopologyDeploymentContext();
        Deployment deployment = new Deployment();
        deployment.setPaasId(cloudifyAppId);
        deploymentContext.setDeployment(deployment);
        if (StringUtils.isNotBlank(nodeName)) {
            cloudifyPaaSPovider.switchInstanceMaintenanceMode(deploymentContext, nodeName, instanceId, on);
        } else {
            cloudifyPaaSPovider.switchMaintenanceMode(deploymentContext, on);
        }
    }

}
