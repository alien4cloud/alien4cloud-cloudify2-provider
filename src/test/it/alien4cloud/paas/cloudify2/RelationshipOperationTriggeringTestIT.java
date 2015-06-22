package alien4cloud.paas.cloudify2;

import lombok.extern.slf4j.Slf4j;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.model.topology.Topology;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.paas.plan.ToscaRelationshipLifecycleConstants;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-testit.xml")
@Slf4j
public class RelationshipOperationTriggeringTestIT extends GenericRelationshipTriggeringTestCase {
    public RelationshipOperationTriggeringTestIT() {
    }

    @Test
    public void testRelationshipOperationTrigger() throws Throwable {
        this.uploadTestArchives("test-types-1.0-SNAPSHOT");
        String[] computesId = new String[] { "source-comp", "target-comp" };
        String cloudifyAppId = deployTopology("relshipTrigeringTest", computesId, null, null);
        Topology topo = alienDAO.findById(Topology.class, cloudifyAppId);
        this.assertApplicationIsInstalled(cloudifyAppId);
        testEvents(cloudifyAppId, new String[] { "source-comp", "target-comp" }, 30000L, ToscaNodeLifecycleConstants.CREATED,
                ToscaNodeLifecycleConstants.CONFIGURED, ToscaNodeLifecycleConstants.STARTED, ToscaNodeLifecycleConstants.AVAILABLE);

        testRelationsEventsSucceeded(cloudifyAppId, null, lastRelIndex, 10000L, ToscaRelationshipLifecycleConstants.ADD_SOURCE,
                ToscaRelationshipLifecycleConstants.ADD_TARGET);

        scale("source-comp", -1, cloudifyAppId, topo, 10);

        testEvents(cloudifyAppId, new String[] { "source-comp" }, 30000L, ToscaNodeLifecycleConstants.DELETED);

        testRelationsEventsSucceeded(cloudifyAppId, null, lastRelIndex, 20000L, ToscaRelationshipLifecycleConstants.REMOVE_SOURCE);

        testUndeployment(cloudifyAppId);

        // testRelationsEventsSkiped(cloudifyAppId, null, lastRelIndex, 20000L, ToscaRelationshipLifecycleConstants.REMOVE_SOURCE,
        // ToscaRelationshipLifecycleConstants.REMOVE_TARGET);

    }
}
