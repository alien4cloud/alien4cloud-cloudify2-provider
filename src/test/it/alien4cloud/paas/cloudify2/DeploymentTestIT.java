package alien4cloud.paas.cloudify2;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.cloudifysource.restclient.exceptions.RestClientException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.component.repository.exception.CSARVersionAlreadyExistsException;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.IPaaSCallback;
import alien4cloud.paas.exception.PaaSAlreadyDeployedException;
import alien4cloud.paas.exception.PaaSDeploymentException;
import alien4cloud.paas.model.AbstractMonitorEvent;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.PaaSDeploymentContext;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.tosca.parser.ParsingException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

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
    public void deployATopologyWhenNoComputeAreDefinedShouldFail() throws JsonParseException, JsonMappingException, ParsingException,
            CSARVersionAlreadyExistsException, IOException {
        log.info("\n\n >> Executing Test deployATopologyWhenNoComputeAreDefinedShouldFail \n");
        deployTopology("noCompute", null, null);
    }

    @Test
    public void topologyWithShScriptsTests() throws Exception {
        log.info("\n\n >> Executing Test topologyWithShScriptsTests \n");

        String cloudifyAppId = null;
        this.uploadGitArchive("samples", "tomcat-war");
        this.uploadTestArchives("test-types-1.0-SNAPSHOT");
        try {
            String[] computesId = new String[] { "comp_tomcatsh" };
            cloudifyAppId = deployTopology("tomcatSh", computesId, null);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "comp_tomcatsh", 1000L * 120);
            assertHttpCodeEquals(cloudifyAppId, "comp_tomcatsh", "8080", "", HTTP_CODE_OK, null);

            testEvents(cloudifyAppId, new String[] { "comp_tomcatsh", "tomcat" }, ToscaNodeLifecycleConstants.CREATED, ToscaNodeLifecycleConstants.CONFIGURED,
                    ToscaNodeLifecycleConstants.STARTED);

            testUndeployment(cloudifyAppId);

            Iterator<String> idsIter = deployedCloudifyAppIds.iterator();
            while (idsIter.hasNext()) {
                if (idsIter.next().equals(cloudifyAppId)) {
                    idsIter.remove();
                    break;
                }
            }

        } catch (Exception e) {
            log.error("Test Failed", e);
            throw e;
        }
    }

    @Test(expected = PaaSAlreadyDeployedException.class)
    public void applicationAlreadyDeployedTest() throws Exception {
        log.info("\n\n >> Executing Test applicationAlreadyDeployedTest \n");

        this.uploadGitArchive("samples", "tomcat-war");
        this.uploadTestArchives("test-types-1.0-SNAPSHOT");
        String[] computesId = new String[] { "compute", "compute_2" };
        String cloudifyAppId = deployTopology("compute_only", computesId, null);
        Topology topo = alienDAO.findById(Topology.class, cloudifyAppId);
        PaaSTopologyDeploymentContext deploymentContext = new PaaSTopologyDeploymentContext();
        deploymentContext.setDeploymentSetup(null);
        deploymentContext.setTopology(topo);
        deploymentContext.setRecipeId("lol");
        deploymentContext.setDeploymentId(cloudifyAppId);
        Map<String, PaaSNodeTemplate> nodes = topologyTreeBuilderService.buildPaaSNodeTemplate(topo);
        deploymentContext.setPaaSTopology(topologyTreeBuilderService.buildPaaSTopology(nodes));
        cloudifyPaaSPovider.deploy(deploymentContext, null);
    }

    @Test
    public void testConfiguringTwoPaaSProvider() throws Throwable {
        log.info("\n\n >> Executing Test testConfiguringTwoPaaSProvider \n");

        String cloudifyURL2 = "http://129.185.67.36:8100/";
        final int configInitialSTCount = new PluginConfigurationBean().getStorageTemplates().size();
        final int providerCTCount = cloudifyPaaSPovider.getRecipeGenerator().getStorageScriptGenerator().getStorageTemplateMatcher().getStorageTemplates()
                .size();
        final String cloudifyURL = cloudifyPaaSPovider.getCloudifyRestClientManager().getCloudifyURL().toString();

        PluginConfigurationBean pluginConfigurationBean2 = anotherCloudifyPaaSPovider.getPluginConfigurationBean();
        pluginConfigurationBean2.getCloudifyConnectionConfiguration().setCloudifyURL(cloudifyURL2);
        pluginConfigurationBean2.setSynchronousDeployment(true);
        pluginConfigurationBean2.getCloudifyConnectionConfiguration().setVersion("2.7.1");
        pluginConfigurationBean2.getStorageTemplates().add(new StorageTemplate());
        try {
            anotherCloudifyPaaSPovider.setConfiguration(pluginConfigurationBean2);
        } catch (Exception e) {
        }

        assertEquals(configInitialSTCount + 1, anotherCloudifyPaaSPovider.getRecipeGenerator().getStorageScriptGenerator().getStorageTemplateMatcher()
                .getStorageTemplates().size());
        assertEquals(cloudifyURL2, anotherCloudifyPaaSPovider.getCloudifyRestClientManager().getCloudifyURL().toString());

        // check the config of the other one still the same
        assertEquals(providerCTCount, cloudifyPaaSPovider.getRecipeGenerator().getStorageScriptGenerator().getStorageTemplateMatcher().getStorageTemplates()
                .size());
        assertEquals(cloudifyURL, cloudifyPaaSPovider.getCloudifyRestClientManager().getCloudifyURL().toString());

    }

    @Test
    public void testRelationshipToscaEnvVars() throws Throwable {
        this.uploadGitArchive("samples", "tomcat-war");
        this.uploadTestArchives("test-types-1.0-SNAPSHOT");
        String[] computesId = new String[] { "comp_envartest" };
        String cloudifyAppId = deployTopology("envVarTest", computesId, null);
        this.assertApplicationIsInstalled(cloudifyAppId);
        testEvents(cloudifyAppId, new String[] { "comp_envartest", "test_component" }, ToscaNodeLifecycleConstants.CREATED,
                ToscaNodeLifecycleConstants.CONFIGURED, ToscaNodeLifecycleConstants.STARTED, ToscaNodeLifecycleConstants.AVAILABLE);
    }

    private void testUndeployment(String applicationId) throws RestClientException {
        PaaSDeploymentContext deploymentContext = new PaaSDeploymentContext();
        deploymentContext.setDeploymentId(applicationId);
        cloudifyPaaSPovider.undeploy(deploymentContext, null);
        assertApplicationIsUninstalled(applicationId);
    }

    private void assertApplicationIsUninstalled(String applicationId) throws RestClientException {

        // RestClient restClient = cloudifyRestClientManager.getRestClient();
        // ApplicationDescription appliDesc = restClient.getApplicationDescription(applicationId);
        // Assert.assertNull("Application " + applicationId + " is not undeloyed!", appliDesc);

        // FIXME this is a hack, for the provider to set the status of the application to UNDEPLOYED
        cloudifyPaaSPovider.getEventsSince(new Date(), 1, new IPaaSCallback<AbstractMonitorEvent[]>() {
            @Override
            public void onSuccess(AbstractMonitorEvent[] abstractMonitorEvents) {
            }

            @Override
            public void onFailure(Throwable throwable) {
            }
        });
        DeploymentStatus status = cloudifyPaaSPovider.getStatus(applicationId);
        Assert.assertEquals("Application " + applicationId + " is not in UNDEPLOYED state", DeploymentStatus.UNDEPLOYED, status);
    }

}
