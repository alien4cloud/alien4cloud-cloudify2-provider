package alien4cloud.paas.cloudify2;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.model.topology.Topology;
import alien4cloud.paas.cloudify2.events.RelationshipOperationEvent;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.paas.plan.ToscaRelationshipLifecycleConstants;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-testit.xml")
@Slf4j
public class RelationshipOperationTriggeringTestIT extends GenericTestCase {
    private static final String GET_REL_EVENTS_END_POINT = "/events/getRelEvents";
    private static final String SERVICE_KEY = "service";
    private static final String APPLICATION_KEY = "application";
    private Integer lastRelIndex = 0;

    public RelationshipOperationTriggeringTestIT() {
    }

    @Test
    public void testRelationshipOperationTrigger() throws Throwable {
        this.uploadGitArchive("samples", "tomcat-war");
        this.uploadTestArchives("test-types-1.0-SNAPSHOT");
        String[] computesId = new String[] { "source_comp", "target_comp" };
        String cloudifyAppId = deployTopology("relshipTrigeringTest", computesId, null, null);
        Topology topo = alienDAO.findById(Topology.class, cloudifyAppId);
        this.assertApplicationIsInstalled(cloudifyAppId);
        testEvents(cloudifyAppId, new String[] { "source_comp", "target_comp" }, 30000L, ToscaNodeLifecycleConstants.CREATED,
                ToscaNodeLifecycleConstants.CONFIGURED, ToscaNodeLifecycleConstants.STARTED, ToscaNodeLifecycleConstants.AVAILABLE);

        testRelationsEventsSucceeded(cloudifyAppId, null, lastRelIndex, 10000L, ToscaRelationshipLifecycleConstants.ADD_SOURCE,
                ToscaRelationshipLifecycleConstants.ADD_TARGET);

        scale("source_comp", -1, cloudifyAppId, topo, 10);

        testEvents(cloudifyAppId, new String[] { "source_comp" }, 30000L, ToscaNodeLifecycleConstants.DELETED);

        testRelationsEventsSucceeded(cloudifyAppId, null, lastRelIndex, 20000L, ToscaRelationshipLifecycleConstants.REMOVE_SOURCE);

        testUndeployment(cloudifyAppId);

        // testRelationsEventsSkiped(cloudifyAppId, null, lastRelIndex, 20000L, ToscaRelationshipLifecycleConstants.REMOVE_SOURCE,
        // ToscaRelationshipLifecycleConstants.REMOVE_TARGET);

    }

    private List<RelationshipOperationEvent> getRelationsEvents(String application, String service, Integer beginIndex) throws Throwable {
        // check that the events module can be reached too.
        URI restEventEndpoint = cloudifyRestClientManager.getRestEventEndpoint();
        URIBuilder builder = new URIBuilder(restEventEndpoint.resolve(GET_REL_EVENTS_END_POINT)).addParameter(APPLICATION_KEY, application)
                .addParameter(SERVICE_KEY, service).addParameter("lastIndex", beginIndex != null ? String.valueOf(beginIndex + 1) : null);
        HttpGet request = new HttpGet(builder.build());
        HttpResponse httpResponse = new DefaultHttpClient().execute(request);
        String response = EntityUtils.toString(httpResponse.getEntity());
        return new ObjectMapper().readValue(response, new TypeReference<List<RelationshipOperationEvent>>() {
        });
    }

    private void testRelationsEventsSucceeded(String application, String nodeName, Integer beginIndex, long timeoutInMillis, String... expectedEvents)
            throws Throwable {
        testRelationsEventsStatus(application, nodeName, beginIndex, timeoutInMillis, true, true, true, expectedEvents);
    }

    // private void testRelationsEventsSkiped(String application, String nodeName, Integer beginIndex, long timeoutInMillis, String... expectedEvents)
    // throws Throwable {
    // testRelationsEventsStatus(application, nodeName, beginIndex, timeoutInMillis, true, false, null, expectedEvents);
    // }

    private void testRelationsEventsStatus(String application, String nodeName, Integer beginIndex, long timeoutInMillis, Boolean processed, Boolean executed,
            Boolean succeeded, String... expectedEvents) throws Throwable, InterruptedException {
        long timeout = System.currentTimeMillis() + timeoutInMillis;
        List<RelationshipOperationEvent> relEvents;
        boolean passed = false;
        do {
            relEvents = getAndAssertRelEventsFired(application, nodeName, beginIndex, timeoutInMillis, expectedEvents);
            passed = assertRelEvents(relEvents, processed, executed, succeeded);
        } while (System.currentTimeMillis() < timeout && !passed);
        log.info("Application: " + application + " got Relationships events : " + relEvents);
        Assert.assertTrue("Status not matched as expected! processed: " + processed + ", executed:" + executed + ", success:" + succeeded
                + ".\n\t got events: " + relEvents, passed);
    }

    private List<RelationshipOperationEvent> getAndAssertRelEventsFired(String application, String nodeName, Integer beginIndex, long timeoutInMillis,
            String... expectedEvents) throws Throwable, InterruptedException {
        Set<String> currentEvents = new HashSet<>();
        List<RelationshipOperationEvent> relEvents = Lists.newArrayList();
        Set<String> expected = Sets.newHashSet(expectedEvents);

        long timeout = System.currentTimeMillis() + timeoutInMillis;
        boolean passed = false;
        do {
            currentEvents.clear();
            relEvents.clear();
            List<RelationshipOperationEvent> Events = getRelationsEvents(application, nodeName, beginIndex);
            for (RelationshipOperationEvent event : Events) {
                if (expected.contains(event.getEvent())) {
                    currentEvents.add(event.getEvent());
                    relEvents.add(event);
                }
            }
            passed = currentEvents.equals(expected);
            if (!passed) {
                Thread.sleep(1000L);
            }
        } while (System.currentTimeMillis() < timeout && !passed);
        Assert.assertTrue("Missing events : " + getMissingEvents(expected, currentEvents), passed);
        return relEvents;
    }

    private boolean assertRelEvents(List<RelationshipOperationEvent> events, Boolean processed, Boolean executed, Boolean succeeded) {
        Set<Boolean> processedSet = Sets.newHashSet();
        Set<Boolean> executedSet = Sets.newHashSet();
        Set<Boolean> succeededSet = Sets.newHashSet();
        Integer higherIndex = 0;
        for (RelationshipOperationEvent event : events) {
            processedSet.add(event.getProcessed());
            executedSet.add(event.getExecuted());
            succeededSet.add(event.getSucceeded());
            higherIndex = higherIndex < event.getEventIndex() ? event.getEventIndex() : higherIndex;
        }

        boolean passed = processedSet.equals(Sets.<Boolean> newHashSet(processed)) && executedSet.equals(Sets.<Boolean> newHashSet(executed))
                && succeededSet.equals(Sets.<Boolean> newHashSet(succeeded));
        if (passed) {
            lastRelIndex = higherIndex;
        }
        return passed;
    }
}
