package alien4cloud.paas.cloudify2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.HashedMap;
import org.cloudifysource.dsl.rest.response.ApplicationDescription;
import org.cloudifysource.dsl.rest.response.ServiceDescription;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.paas.cloudify2.events.AlienEvent;
import alien4cloud.paas.cloudify2.events.BlockStorageEvent;
import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.exception.PaaSAlreadyDeployedException;
import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.paas.plan.PlanGeneratorConstants;
import alien4cloud.tosca.container.model.topology.Topology;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-testit.xml")
@Slf4j
public class StorageAndCommandTestIT extends GenericTestCase {

    @Resource(name = "cloudify-paas-provider-bean")
    protected CloudifyPaaSProvider anotherCloudifyPaaSPovider;

    public StorageAndCommandTestIT() {
    }

    // @Override
    // public void after() {
    // // TODO Auto-generated method stub
    // // super.after();
    // }

    @Test
    public void customCommandTest() throws Exception {
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
        String cloudifyAppId = null;
        this.initElasticSearch(new String[] { "tosca-normative-types", "fastconnect-base-types", "tomcat-test-types" }, new String[] { "1.0.0-wd02-SNAPSHOT",
                "0.1.1", "0.3-snapshot" });
        try {
            String[] computesId = new String[] { "serveur_web" };
            cloudifyAppId = deployTopology("computeBlockStorageWithVolumeId", computesId, true);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "serveur_web", 1000L * 120);
            assertHttpCodeEquals(cloudifyAppId, "serveur_web", "8080", "", HTTP_CODE_OK, null);
            assertStorageEventFiredWithVolumeId(cloudifyAppId, new String[] { "blockstorage" }, PlanGeneratorConstants.STATE_CREATED);

        } catch (Exception e) {
            log.error("Test Failed", e);
            throw e;
        }
    }

    @Test
    @Ignore
    public void blockStorageSizeProvidedSucessTest() throws Exception {
        String cloudifyAppId = null;
        this.initElasticSearch(new String[] { "tosca-normative-types", "fastconnect-base-types", "tomcat-test-types" }, new String[] { "1.0.0-wd02-SNAPSHOT",
                "0.1.1", "0.3-snapshot" });
        try {

            String[] computesId = new String[] { "serveur_web" };
            cloudifyAppId = deployTopology("computeBlockStorageWithSize", computesId, true);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "serveur_web", 1000L * 120);
            assertHttpCodeEquals(cloudifyAppId, "serveur_web", "8080", "", HTTP_CODE_OK, null);

        } catch (Exception e) {
            log.error("Test Failed", e);
            throw e;
        }
    }

    @Test(expected = PaaSAlreadyDeployedException.class)
    public void applicationAlreadyDeployedTest() throws Exception {
        this.initElasticSearch(new String[] { "tosca-base-types" }, new String[] { "1.0" });
        String[] computesId = new String[] { "compute" };
        String cloudifyAppId = deployTopology("compute_only", computesId, true);
        Topology topo = alienDAO.findById(Topology.class, cloudifyAppId);
        cloudifyPaaSPovider.deploy("lol", cloudifyAppId, topo, null);
    }

    @Test
    public void testConfiguringTwoPaaSProvider() throws Throwable {

        String cloudifyURL2 = "http://129.185.67.36:8100/";
        final int configInitialSTCount = new PluginConfigurationBean().getStorageTemplates().size();
        final int providerCTCount = cloudifyPaaSPovider.getRecipeGenerator().getStorageTemplateMatcher().getStorageTemplates().size();
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

        assertEquals(configInitialSTCount + 1, anotherCloudifyPaaSPovider.getRecipeGenerator().getStorageTemplateMatcher().getStorageTemplates().size());
        assertEquals(cloudifyURL2, anotherCloudifyPaaSPovider.getCloudifyRestClientManager().getCloudifyURL().toString());

        // check the config of the other one still the same
        assertEquals(providerCTCount, cloudifyPaaSPovider.getRecipeGenerator().getStorageTemplateMatcher().getStorageTemplates().size());
        assertEquals(cloudifyURL, cloudifyPaaSPovider.getCloudifyRestClientManager().getCloudifyURL().toString());

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

    private void assertStorageEventFiredWithVolumeId(String cloudifyAppId, String[] nodeTemplateNames, String... expectedEvents) throws Throwable {
        ApplicationDescription applicationDescription = cloudifyRestClientManager.getRestClient().getApplicationDescription(cloudifyAppId);
        for (String nodeName : nodeTemplateNames) {
            for (ServiceDescription service : applicationDescription.getServicesDescription()) {
                String applicationName = service.getApplicationName();
                String serviceName = nodeName;
                CloudifyEventsListener listener = new CloudifyEventsListener(cloudifyRestClientManager.getRestEventEndpoint(), applicationName, serviceName);
                List<AlienEvent> allServiceEvents = listener.getEvents();

                Set<String> currentEvents = new HashSet<>();
                for (AlienEvent alienEvent : allServiceEvents) {
                    currentEvents.add(alienEvent.getEvent());
                    if (alienEvent.getEvent().equalsIgnoreCase(PlanGeneratorConstants.STATE_CREATED)) {
                        assertTrue("Event is supposed to be a BlockStorageEvent instance", alienEvent instanceof BlockStorageEvent);
                        Assert.assertNotNull(((BlockStorageEvent) alienEvent).getVolumeId());
                    }
                }
                log.info("Application: " + applicationName + "." + serviceName + " got events : " + currentEvents);
                Assert.assertTrue("Missing events: " + getMissingEvents(Sets.newHashSet(expectedEvents), currentEvents),
                        currentEvents.containsAll(Sets.newHashSet(expectedEvents)));
            }
        }

    }

}
