package alien4cloud.paas.cloudify2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.map.HashedMap;
import org.cloudifysource.dsl.rest.response.ApplicationDescription;
import org.cloudifysource.dsl.rest.response.ServiceDescription;
import org.cloudifysource.restclient.exceptions.RestClientException;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.component.repository.exception.CSARVersionAlreadyExistsException;
import alien4cloud.paas.cloudify2.events.AlienEvent;
import alien4cloud.paas.cloudify2.events.BlockStorageEvent;
import alien4cloud.paas.exception.OperationExecutionException;
import alien4cloud.paas.exception.PaaSAlreadyDeployedException;
import alien4cloud.paas.exception.ResourceMatchingFailedException;
import alien4cloud.paas.model.DeploymentStatus;
import alien4cloud.paas.model.NodeOperationExecRequest;
import alien4cloud.paas.plan.PlanGeneratorConstants;
import alien4cloud.tosca.container.exception.CSARParsingException;
import alien4cloud.tosca.container.exception.CSARValidationException;
import alien4cloud.tosca.container.model.topology.Topology;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-testit.xml")
@Slf4j
public class CloudifyPaaSPoviderTestIT extends GenericTestCase {

    @Resource(name = "cloudify-paas-provider-bean")
    protected CloudifyPaaSProvider anotherCloudifyPaaSPovider;

    public CloudifyPaaSPoviderTestIT() {
    }

    @Override
    public void after() {
        // TODO Auto-generated method stub
        // super.after();
    }

    @Test(expected = ResourceMatchingFailedException.class)
    public void deployATopologyWhenNoComputeAreDefinedShouldFail() throws JsonParseException, JsonMappingException, CSARParsingException,
            CSARVersionAlreadyExistsException, IOException, CSARValidationException {
        this.initElasticSearch(new String[] { "apache-lb-types", "tomcat-types", "tomcatGroovy-types" }, new String[] { "0.1", "0.1", "0.1" });

        deployTopology("petclinic-nocompute", false);
    }

    @Test
    public void deployAndUndeployTomcat() throws Exception {
        String cloudifyAppId = null;
        // uploadCsar("tosca-base-types", "1.0");
        // uploadCsar("fastconnect-base-types", "0.1");
        this.initElasticSearch(new String[] { "tosca-base-types", "fastconnect-base-types", "apache-lb-types", "tomcat-types", "tomcatGroovy-types" },
                new String[] { "1.0", "0.1", "0.1", "0.1", "0.1" });
        try {
            cloudifyAppId = deployTopology("tomcat", false);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "computetomcat", 1000L * 120);
            assertHttpCodeEquals(cloudifyAppId, "computetomcat", "8080", "", HTTP_CODE_OK, null);

            testEvents(cloudifyAppId, new String[] { "ComputeTomcat", "Tomcat" }, PlanGeneratorConstants.STATE_CREATING, PlanGeneratorConstants.STATE_CREATED,
                    PlanGeneratorConstants.STATE_CONFIGURING, PlanGeneratorConstants.STATE_CONFIGURED, PlanGeneratorConstants.STATE_STARTING,
                    PlanGeneratorConstants.STATE_STARTED);

            testUndeployment(cloudifyAppId);

            // testEvents(applicationId, new String[] { "ComputeTomcat", "Tomcat" }, PlanGeneratorConstants.STATE_STOPPING,
            // PlanGeneratorConstants.STATE_STOPPED);

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

    @Test
    public void topologyWithShScriptsTests() throws Exception {

        String cloudifyAppId = null;
        this.initElasticSearch(new String[] { "tosca-base-types", "fastconnect-base-types", "apache-types", "tomcat-test-types" }, new String[] { "1.0", "0.1",
                "0.1.1", "0.1.1" });
        try {
            cloudifyAppId = deployTopology("tomcatShApache", true);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "serveur_web", 1000L * 120);
            assertHttpCodeEquals(cloudifyAppId, "serveur_web", "8080", "", HTTP_CODE_OK, null);
            assertHttpCodeEquals(cloudifyAppId, "serveur_web", "80", "", HTTP_CODE_OK, null);

            testEvents(cloudifyAppId, new String[] { "serveur_web", "apache", "tomcat" }, PlanGeneratorConstants.STATE_CREATING,
                    PlanGeneratorConstants.STATE_CREATED, PlanGeneratorConstants.STATE_CONFIGURING, PlanGeneratorConstants.STATE_CONFIGURED,
                    PlanGeneratorConstants.STATE_STARTING, PlanGeneratorConstants.STATE_STARTED);

            testUndeployment(cloudifyAppId);

            // testEvents(applicationId, new String[] { "ComputeTomcat", "Tomcat" }, PlanGeneratorConstants.STATE_STOPPING,
            // PlanGeneratorConstants.STATE_STOPPED);

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

    @Test
    public void customCommandTest() throws Exception {
        String cloudifyAppId = null;
        this.initElasticSearch(new String[] { "tosca-base-types", "fastconnect-base-types", "tomcat-test-types" },
                new String[] { "1.0", "0.1", "0.2-snapshot" });
        try {
            cloudifyAppId = deployTopology("customCmd", true);

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
            cloudifyAppId = deployTopology("computeBlockStorageWithVolumeId", true);

            this.assertApplicationIsInstalled(cloudifyAppId);
            waitForServiceToStarts(cloudifyAppId, "serveur_web", 1000L * 120);
            assertHttpCodeEquals(cloudifyAppId, "serveur_web", "8080", "", HTTP_CODE_OK, null);
            assertStorageEventFiredWithVolumeId(cloudifyAppId, new String[] { "blockstorage" }, PlanGeneratorConstants.STATE_CREATED);

        } catch (Exception e) {
            log.error("Test Failed", e);
            throw e;
        }
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

    @Test
    @Ignore
    public void blockStorageSizeProvidedSucessTest() throws Exception {
        String cloudifyAppId = null;
        this.initElasticSearch(new String[] { "tosca-normative-types", "fastconnect-base-types", "tomcat-test-types" }, new String[] { "1.0.0-wd02-SNAPSHOT",
                "0.1.1", "0.3-snapshot" });
        try {
            cloudifyAppId = deployTopology("computeBlockStorageWithSize", true);

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

        String cloudifyAppId = deployTopology("compute_only", true);
        Topology topo = alienDAO.findById(Topology.class, cloudifyAppId);
        cloudifyPaaSPovider.deploy("lol", cloudifyAppId, topo, null);
    }

    @Test
    public void testConfiguringTwoPaaSProvider() throws Throwable {

        String cloudifyURL2 = "http://129.185.67.36:8100/";
        final int initialCTCount = new PluginConfigurationBean().getComputeTemplates().size();

        assertEquals(initialCTCount + 1, cloudifyPaaSPovider.getRecipeGenerator().getComputeTemplateMatcher().getComputeTemplates().size());
        String cloudifyURL = cloudifyPaaSPovider.getCloudifyRestClientManager().getCloudifyURL().toString();

        PluginConfigurationBean pluginConfigurationBean2 = anotherCloudifyPaaSPovider.getPluginConfigurationBean();
        pluginConfigurationBean2.getCloudifyConnectionConfiguration().setCloudifyURL(cloudifyURL2);
        pluginConfigurationBean2.setSynchronousDeployment(true);
        pluginConfigurationBean2.getCloudifyConnectionConfiguration().setVersion("2.7.1");
        try {
            anotherCloudifyPaaSPovider.setConfiguration(pluginConfigurationBean2);
        } catch (Exception e) {
        }

        assertEquals(initialCTCount, anotherCloudifyPaaSPovider.getRecipeGenerator().getComputeTemplateMatcher().getComputeTemplates().size());
        assertEquals(cloudifyURL2, anotherCloudifyPaaSPovider.getCloudifyRestClientManager().getCloudifyURL().toString());

        // check the config of the other one still the same
        assertEquals(initialCTCount + 1, cloudifyPaaSPovider.getRecipeGenerator().getComputeTemplateMatcher().getComputeTemplates().size());
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

    private void testUndeployment(String applicationId) throws RestClientException {
        cloudifyPaaSPovider.undeploy(applicationId);
        assertApplicationIsUninstalled(applicationId);
    }

    private void testEvents(String applicationId, String[] nodeTemplateNames, String... expectedEvents) throws Exception {
        ApplicationDescription applicationDescription = cloudifyRestClientManager.getRestClient().getApplicationDescription(applicationId);
        for (String nodeName : nodeTemplateNames) {
            this.assertFiredEvents(nodeName, new HashSet<String>(Arrays.asList(expectedEvents)), applicationDescription);
        }
    }

    private void assertApplicationIsUninstalled(String applicationId) throws RestClientException {

        // RestClient restClient = cloudifyRestClientManager.getRestClient();
        // ApplicationDescription appliDesc = restClient.getApplicationDescription(applicationId);
        // Assert.assertNull("Application " + applicationId + " is not undeloyed!", appliDesc);

        // FIXME this is a hack, for the provider to set the status of the application to UNDEPLOYED
        cloudifyPaaSPovider.getEventsSince(new Date(), 1);
        DeploymentStatus status = cloudifyPaaSPovider.getStatus(applicationId);
        Assert.assertEquals("Application " + applicationId + " is not in UNDEPLOYED state", DeploymentStatus.UNDEPLOYED, status);
    }

    private void assertFiredEvents(String nodeName, Set<String> expectedEvents, ApplicationDescription applicationDescription) throws Exception {

        for (ServiceDescription service : applicationDescription.getServicesDescription()) {
            String applicationName = service.getApplicationName();
            String serviceName = nodeName;
            CloudifyEventsListener listener = new CloudifyEventsListener(cloudifyRestClientManager.getRestEventEndpoint(), applicationName, serviceName);
            List<AlienEvent> allServiceEvents = listener.getEvents();

            Set<String> currentEvents = new HashSet<>();
            for (AlienEvent alienEvent : allServiceEvents) {
                currentEvents.add(alienEvent.getEvent());
            }
            log.info("Application: " + applicationName + "." + serviceName + " got events : " + currentEvents);
            Assert.assertTrue("Missing events: " + getMissingEvents(expectedEvents, currentEvents), currentEvents.containsAll(expectedEvents));
        }
    }

    private Set<String> getMissingEvents(Set<String> expectedEvents, Set<String> currentEvents) {
        Set<String> missing = new HashSet<>();
        for (String event : expectedEvents) {
            if (!currentEvents.contains(event)) {
                missing.add(event);
            }
        }
        return missing;
    }

}
