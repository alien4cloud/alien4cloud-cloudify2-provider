package alien4cloud.paas.cloudify2;

import java.nio.file.Files;
import java.nio.file.Paths;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.component.repository.ArtifactLocalRepository;
import alien4cloud.tosca.container.model.topology.NodeTemplate;
import alien4cloud.tosca.container.model.topology.Topology;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-testit.xml")
public class ArtifactsCopyTestIT extends GenericTestCase {

    @Resource
    private ArtifactLocalRepository artifactRepository;

    public ArtifactsCopyTestIT() {
    }

    @Override
    public void after() {
        // // TODO Auto-generated method stub
        // super.after();
    }

    @Test
    public void testOverridingArtifacts() throws Exception {
        String cloudifyAppId = null;

        this.initElasticSearch(new String[] { "tosca-normative-types", "fastconnect-base-types", "tomcat-test-types" }, new String[] { "1.0.0-wd02-SNAPSHOT",
                "0.1.1", "0.4-snapshot" });

        String artifactId = "helloWorld2.war";
        artifactRepository.storeFile(artifactId, Files.newInputStream(Paths.get("src/test/resources/data/helloWorld2.war")));
        cloudifyAppId = deployTopology("tomcatWar", true);
        assertApplicationIsInstalled(cloudifyAppId);

        String serviceName = "comp_tomcat_war";
        Topology topology = alienDAO.findById(Topology.class, cloudifyAppId);
        NodeTemplate war1 = topology.getNodeTemplates().get("war_1");
        NodeTemplate war2 = topology.getNodeTemplates().get("war_2");

        assertHttpCodeEquals(cloudifyAppId, serviceName, DEFAULT_TOMCAT_PORT, "", HTTP_CODE_OK, null);
        assertHttpCodeEquals(cloudifyAppId, serviceName, DEFAULT_TOMCAT_PORT, war1.getProperties().get("contextPath"), HTTP_CODE_OK, 20 * 1000);
        assertHttpCodeEquals(cloudifyAppId, serviceName, DEFAULT_TOMCAT_PORT, war2.getProperties().get("contextPath"), HTTP_CODE_OK, 20 * 1000);
    }
}