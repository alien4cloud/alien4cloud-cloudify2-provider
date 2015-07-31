package alien4cloud.paas.cloudify2;

import java.nio.file.Files;
import java.nio.file.Paths;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.component.repository.ArtifactLocalRepository;
import alien4cloud.component.repository.ArtifactRepositoryConstants;
import alien4cloud.model.components.DeploymentArtifact;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.function.FunctionEvaluator;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;

import com.google.common.collect.Maps;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-testit.xml")
@Slf4j
public class ArtifactsCopyTestIT extends GenericTestCase {

    @Resource
    private ArtifactLocalRepository artifactRepository;

    public ArtifactsCopyTestIT() {
    }

    @Test
    public void testOverridingArtifacts() throws Throwable {

        log.info("\n\n >> Executing Test testOverridingArtifacts \n");

        String cloudifyAppId = null;

        this.uploadGitArchive("samples", null, "tomcat-war");
        String topologyFileName = "tomcatWar";
        String artifacName = "helloWorld2.war";
        String artifactId = artifactRepository.storeFile(Files.newInputStream(Paths.get("src/test/resources/data/helloWorld2.war")));
        Topology topology = createAlienApplication(topologyFileName, topologyFileName);
        DeploymentArtifact artifact = new DeploymentArtifact();
        artifact.setArtifactName(artifacName);
        artifact.setArtifactRef(artifactId);
        artifact.setArtifactRepository(ArtifactRepositoryConstants.ALIEN_ARTIFACT_REPOSITORY);
        topology.getNodeTemplates().get("war_2").setArtifacts(Maps.<String, DeploymentArtifact> newHashMap());
        topology.getNodeTemplates().get("war_2").getArtifacts().put("war_file", artifact);
        alienDAO.save(topology);
        String[] computes = new String[] { "comp-tomcat-war" };
        cloudifyAppId = deployTopology(computes, topology, topologyFileName, null, null);
        assertApplicationIsInstalled(cloudifyAppId);
        testEvents(cloudifyAppId, new String[] { "comp-tomcat-war", "java", "tomcat", "War_1", "war_2" }, 30000L, ToscaNodeLifecycleConstants.CREATED,
                ToscaNodeLifecycleConstants.CONFIGURED, ToscaNodeLifecycleConstants.STARTED);

        String serviceName = "comp-tomcat-war";
        NodeTemplate war1 = topology.getNodeTemplates().get("War_1");
        NodeTemplate war2 = topology.getNodeTemplates().get("war_2");

        String tomcatPort = FunctionEvaluator.getScalarValue(topology.getNodeTemplates().get("tomcat").getProperties().get("tomcat_port"));

        assertHttpCodeEquals(cloudifyAppId, serviceName, tomcatPort, "", HTTP_CODE_OK, null);
        assertHttpCodeEquals(cloudifyAppId, serviceName, tomcatPort, FunctionEvaluator.getScalarValue(war1.getProperties().get("context_path")), HTTP_CODE_OK,
                20 * 1000);
        assertHttpCodeEquals(cloudifyAppId, serviceName, tomcatPort, FunctionEvaluator.getScalarValue(war2.getProperties().get("context_path")), HTTP_CODE_OK,
                20 * 1000);
    }
}