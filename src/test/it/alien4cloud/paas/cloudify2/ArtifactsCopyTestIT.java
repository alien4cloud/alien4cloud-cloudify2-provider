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
import alien4cloud.tosca.container.model.template.DeploymentArtifact;
import alien4cloud.tosca.container.model.topology.NodeTemplate;
import alien4cloud.tosca.container.model.topology.Topology;

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
    public void testOverridingArtifacts() throws Exception {

        log.info("\n\n >> Executing Test testOverridingArtifacts \n");

        String cloudifyAppId = null;

        this.initElasticSearch(new String[] { "tosca-normative-types", "fastconnect-base-types", "tomcat-test-types" }, new String[] { "1.0.0-wd02-SNAPSHOT",
                "0.1.1", "0.4-snapshot" });
        String topologyFileName = "tomcatWar";
        String artifacName = "helloWorld2.war";
        String artifactId = artifactRepository.storeFile(Files.newInputStream(Paths.get("src/test/resources/data/helloWorld2.war")));
        Topology topology = createAlienApplication(topologyFileName, topologyFileName, true);
        DeploymentArtifact artifact = new DeploymentArtifact();
        artifact.setArtifactName(artifacName);
        artifact.setArtifactRef(artifactId);
        artifact.setArtifactRepository(ArtifactRepositoryConstants.ALIEN_ARTIFACT_REPOSITORY);
        topology.getNodeTemplates().get("war_2").setArtifacts(Maps.<String, DeploymentArtifact> newHashMap());
        topology.getNodeTemplates().get("war_2").getArtifacts().put("war_file", artifact);
        alienDAO.save(topology);
        String[] computes = new String[] { "comp_tomcat_war" };
        cloudifyAppId = deployTopology(computes, topology, topologyFileName);
        assertApplicationIsInstalled(cloudifyAppId);

        String serviceName = "comp_tomcat_war";
        NodeTemplate war1 = topology.getNodeTemplates().get("war_1");
        NodeTemplate war2 = topology.getNodeTemplates().get("war_2");

        assertHttpCodeEquals(cloudifyAppId, serviceName, DEFAULT_TOMCAT_PORT, "", HTTP_CODE_OK, null);
        assertHttpCodeEquals(cloudifyAppId, serviceName, DEFAULT_TOMCAT_PORT, war1.getProperties().get("contextPath"), HTTP_CODE_OK, 20 * 1000);
        assertHttpCodeEquals(cloudifyAppId, serviceName, DEFAULT_TOMCAT_PORT, war2.getProperties().get("contextPath"), HTTP_CODE_OK, 20 * 1000);
    }
}