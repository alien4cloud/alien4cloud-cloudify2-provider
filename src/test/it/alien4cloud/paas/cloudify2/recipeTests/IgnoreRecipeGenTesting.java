package alien4cloud.paas.cloudify2.recipeTests;

import java.io.IOException;
import java.util.Map;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.application.ApplicationService;
import alien4cloud.component.repository.exception.CSARVersionAlreadyExistsException;
import alien4cloud.dao.ElasticSearchDAO;
import alien4cloud.model.application.Application;
import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.model.cloud.CloudImage;
import alien4cloud.model.cloud.CloudImageFlavor;
import alien4cloud.model.cloud.CloudResourceMatcherConfig;
import alien4cloud.model.cloud.ComputeTemplate;
import alien4cloud.model.cloud.StorageTemplate;
import alien4cloud.model.deployment.Deployment;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.cloudify2.DeploymentPropertiesNames;
import alien4cloud.paas.cloudify2.testutils.TestsUtils;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSTopology;
import alien4cloud.paas.model.PaaSTopologyDeploymentContext;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.tosca.parser.ParsingException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.google.common.collect.Maps;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-testit.xml")
@Slf4j
@Ignore
public class IgnoreRecipeGenTesting {

    protected static final String DEFAULT_LINUX_COMPUTE_TEMPLATE_ID = "MEDIUM_LINUX";

    @Resource(name = "recipe-gen-provider")
    protected RecipeGenProvider cloudifyPaaSPovider;
    @Resource
    private ApplicationService applicationService;
    @Resource
    protected TestsUtils testsUtils;
    @Resource
    protected ElasticSearchDAO alienDAO;

    @Resource
    private TopologyTreeBuilderService topologyTreeBuilderService;

    @Before
    public void before() throws Throwable {

        CloudResourceMatcherConfig matcherConf = new CloudResourceMatcherConfig();

        Map<CloudImage, String> imageMapping = Maps.newHashMap();
        CloudImage cloudImage = new CloudImage();
        cloudImage.setId("ALIEN_LINUX_IMAGE");
        imageMapping.put(cloudImage, "IAAS_IMAGE_ID");
        matcherConf.setImageMapping(imageMapping);

        Map<CloudImageFlavor, String> flavorMapping = Maps.newHashMap();
        flavorMapping.put(new CloudImageFlavor("ALIEN_FLAVOR", 1, 1L, 1L), "IAAS_FLAVOR_ID");
        matcherConf.setFlavorMapping(flavorMapping);

        Map<StorageTemplate, String> storageMapping = Maps.newHashMap();
        storageMapping.put(new StorageTemplate("ALIEN_STORAGE", 1L, "ALIEN_STORAGE_DEVICE", null), "IAAS_BLOCK_STORAGE_ID");
        matcherConf.setStorageMapping(storageMapping);
        cloudifyPaaSPovider.updateMatcherConfig(matcherConf);

        testsUtils.uploadGitArchive("tosca-normative-types-1.0.0.wd03", "", "");
        testsUtils.uploadGitArchive("alien-extended-types", "", "alien-base-types-1.0-SNAPSHOT");
    }

    @BeforeClass
    public static void beforeClass() {
        TestsUtils.cleanAlienTargetDir();
    }

    @Test
    public void deployApplication() throws Exception {
        testsUtils.uploadArchive("test-types-1.0-SNAPSHOT");
        // testsUtils.uploadCsarFile("C:\\Users\\igor\\Projets\\ALIEN\\csar\\Archive");
        // testsUtils.uploadCsarFile("C:\\Users\\igor\\Projets\\ALIEN\\csar\\repositories\\samples\\tomcat-war");
        testsUtils.uploadGitArchive("alien-extended-types", "", "alien-extended-storage-types-1.0-SNAPSHOT");
        testsUtils.uploadArchive("custom-storage-types-1.0-SNAPSHOT");

        // String[] computes = new String[] { "comp_tomcatsh" };
        String[] computes = new String[] { "comp_storage_volumeid" };
        // String[] computes = new String[] { "comp_getOpOutputTarget", "comp_getOpOutputSource" };
        String cloudifyAppId = deployTopology("blockStorageWithVolumeId", computes, null);
    }

    @Test
    public void test() throws Exception {
        testsUtils.uploadCsarFile("C:\\Users\\igor\\SANDBOX\\support");
        String[] computes = new String[] { "compute" };
        String cloudifyAppId = deployTopology("eraseMe", computes, null);
    }

    protected String deployTopology(String topologyFileName, String[] computesId, Map<String, ComputeTemplate> computesMatching) throws IOException,
            JsonParseException, JsonMappingException, CSARVersionAlreadyExistsException, ParsingException {
        Topology topology = testsUtils.parseYamlTopology(topologyFileName);
        String applicationId = applicationService.create("alien", topologyFileName, null, null);
        topology.setDelegateId(applicationId);
        topology.setDelegateType(Application.class.getSimpleName().toLowerCase());
        alienDAO.save(topology);

        return deployTopology(computesId, topology, topologyFileName, computesMatching);
    }

    protected String deployTopology(String[] computesId, Topology topology, String topologyFileName, Map<String, ComputeTemplate> computesMatching) {
        DeploymentSetup setup = new DeploymentSetup();
        setup.setCloudResourcesMapping(Maps.<String, ComputeTemplate> newHashMap());
        if (computesId != null) {
            for (String string : computesId) {
                setup.getCloudResourcesMapping().put(string, new ComputeTemplate(null, DEFAULT_LINUX_COMPUTE_TEMPLATE_ID));
            }
        }
        if (computesMatching != null) {
            setup.getCloudResourcesMapping().putAll(computesMatching);
        }

        // configure provider properties

        setup.setProviderDeploymentProperties(Maps.<String, String> newHashMap());
        setup.getProviderDeploymentProperties().put(DeploymentPropertiesNames.STARTDETECTION_TIMEOUT_INSECOND, "800");
        setup.getProviderDeploymentProperties().put(DeploymentPropertiesNames.EVENTS_LEASE_INHOUR, "3");
        log.info("\n\n TESTS: Deploying topology <{}>. Deployment id is <{}>. \n", topologyFileName, topology.getId());
        PaaSTopologyDeploymentContext deploymentContext = new PaaSTopologyDeploymentContext();
        Deployment deployment = new Deployment();
        deployment.setId(topology.getId());
        deployment.setDeploymentSetup(setup);
        deployment.setPaasId(topology.getId());
        deploymentContext.setDeployment(deployment);
        deploymentContext.setTopology(topology);
        Map<String, PaaSNodeTemplate> nodes = topologyTreeBuilderService.buildPaaSNodeTemplates(topology);
        PaaSTopology paaSTopology = topologyTreeBuilderService.buildPaaSTopology(nodes);
        setup.setStorageMapping(Maps.<String, StorageTemplate> newHashMap());
        for (PaaSNodeTemplate volume : paaSTopology.getVolumes()) {
            setup.getStorageMapping().put(volume.getId(), new StorageTemplate("ALIEN_STORAGE", 1L, "ALIEN_STORAGE_DEVICE", null));
        }
        deploymentContext.setPaaSTopology(paaSTopology);
        cloudifyPaaSPovider.deploy(deploymentContext, null);
        return topology.getId();
    }

}
