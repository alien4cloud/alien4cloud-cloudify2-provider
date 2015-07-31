package alien4cloud.paas.cloudify2;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.model.deployment.Deployment;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.exception.PaaSAlreadyDeployedException;
import alien4cloud.paas.exception.PaaSDeploymentException;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;

import com.google.common.collect.Lists;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-testit.xml")
@Slf4j
public class DeploymentTestIT extends GenericTestCase {

    @Resource(name = "cloudify-paas-provider-bean")
    protected CloudifyPaaSProvider anotherCloudifyPaaSPovider;

    @Resource
    private TopologyTreeBuilderService topologyTreeBuilderService;

    public DeploymentTestIT() {
    }

    @Test(expected = PaaSDeploymentException.class)
    public void deployATopologyWhenNoComputeAreDefinedShouldFail() throws Throwable {
        log.info("\n\n >> Executing Test deployATopologyWhenNoComputeAreDefinedShouldFail \n");
        deployTopology("noCompute", null, null, null);
    }

    @Test(expected = PaaSAlreadyDeployedException.class)
    public void applicationAlreadyDeployedTest() throws Throwable {
        log.info("\n\n >> Executing Test applicationAlreadyDeployedTest \n");

        this.uploadGitArchive("samples", null, "tomcat-war");
        this.uploadTestArchives("test-types-1.0-SNAPSHOT");
        String[] computesId = new String[] { "compute", "compute_2" };
        String cloudifyAppId = deployTopology("compute_only", computesId, null, null);
        Topology topo = alienDAO.findById(Topology.class, cloudifyAppId);
        PaaSTopologyDeploymentContext deploymentContext = new PaaSTopologyDeploymentContext();
        deploymentContext.setTopology(topo);
        Deployment deployment = new Deployment();
        deployment.setPaasId(cloudifyAppId);
        deploymentContext.setDeployment(deployment);
        Map<String, PaaSNodeTemplate> nodes = topologyTreeBuilderService.buildPaaSNodeTemplates(topo);
        deploymentContext.setPaaSTopology(topologyTreeBuilderService.buildPaaSTopology(nodes));
        cloudifyPaaSPovider.deploy(deploymentContext, null);
    }

    @Test
    public void testConfiguringTwoPaaSProvider() throws Throwable {
        log.info("\n\n >> Executing Test testConfiguringTwoPaaSProvider \n");

        String cloudifyURL2 = "http://129.185.67.36:8100/";
        final String cloudifyURL = cloudifyPaaSPovider.getCloudifyRestClientManager().getCloudifyURL().toString();

        PluginConfigurationBean pluginConfigurationBean2 = anotherCloudifyPaaSPovider.getPluginConfigurationBean();
        pluginConfigurationBean2.setCloudifyURLs(Lists.newArrayList(cloudifyURL2));
        pluginConfigurationBean2.setVersion("2.7.1");
        pluginConfigurationBean2.setConnectionTimeOutInSeconds(5);
        try {
            anotherCloudifyPaaSPovider.setConfiguration(pluginConfigurationBean2);
        } catch (Exception e) {
        }
        assertEquals(cloudifyURL2, anotherCloudifyPaaSPovider.getCloudifyRestClientManager().getCloudifyURL().toString());
        // check the config of the other one still the same
        assertEquals(cloudifyURL, cloudifyPaaSPovider.getCloudifyRestClientManager().getCloudifyURL().toString());
    }

    @Test
    public void testRelationshipToscaEnvVars() throws Throwable {
        this.uploadGitArchive("samples", null, "tomcat-war");
        this.uploadTestArchives("test-types-1.0-SNAPSHOT");
        String[] computesId = new String[] { "comp-envartest" };
        String cloudifyAppId = deployTopology("envVarTest", computesId, null, null);
        this.assertApplicationIsInstalled(cloudifyAppId);
        testEvents(cloudifyAppId, new String[] { "comp-envartest", "test_component" }, 30000L, ToscaNodeLifecycleConstants.CREATED,
                ToscaNodeLifecycleConstants.CONFIGURED, ToscaNodeLifecycleConstants.STARTED, ToscaNodeLifecycleConstants.AVAILABLE);
    }
}
