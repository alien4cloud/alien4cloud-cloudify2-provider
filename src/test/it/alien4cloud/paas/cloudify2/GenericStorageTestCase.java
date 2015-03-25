package alien4cloud.paas.cloudify2;

import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.cloudifysource.dsl.rest.response.ApplicationDescription;
import org.cloudifysource.dsl.rest.response.ServiceDescription;
import org.junit.Assert;

import alien4cloud.paas.cloudify2.events.AlienEvent;
import alien4cloud.paas.cloudify2.events.BlockStorageEvent;
import alien4cloud.paas.cloudify2.rest.CloudifyEventsListener;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;

import com.google.common.collect.Sets;

@Slf4j
public class GenericStorageTestCase extends GenericTestCase {

    protected void assertStorageEventFiredWithVolumeId(String cloudifyAppId, String[] nodeTemplateNames, String... expectedEvents) throws Throwable {
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
                    if (alienEvent.getEvent().equalsIgnoreCase(ToscaNodeLifecycleConstants.CREATED)) {
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
