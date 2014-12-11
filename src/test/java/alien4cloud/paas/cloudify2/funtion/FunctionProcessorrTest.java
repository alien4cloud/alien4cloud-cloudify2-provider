package alien4cloud.paas.cloudify2.funtion;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.model.application.Application;
import alien4cloud.model.application.ApplicationEnvironment;
import alien4cloud.model.application.ApplicationVersion;
import alien4cloud.model.application.DeploymentSetup;
import alien4cloud.model.deployment.Deployment;
import alien4cloud.paas.cloudify2.generator.CloudifyCommandGenerator;
import alien4cloud.paas.cloudify2.testutils.TestsUtils;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.plan.PlanGeneratorConstants;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.plugin.PluginConfiguration;
import alien4cloud.tosca.container.model.topology.Topology;
import alien4cloud.tosca.model.AbstractPropertyValue;
import alien4cloud.tosca.model.Csar;
import alien4cloud.tosca.model.IOperationParameter;
import alien4cloud.tosca.model.Operation;

import com.google.common.collect.Lists;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-testit.xml")
public class FunctionProcessorrTest {

    private static final String GET_INSTANCE_ATTRIBUTE_FORMAT = "CloudifyAttributesUtils.getAttribute(%s, %s, %s)";
    private static final String GET_IP_FORMAT = "CloudifyAttributesUtils.getIp(%s, %s)";

    @Resource
    private FunctionProcessor processor;

    @Resource
    private TopologyTreeBuilderService treeBuilder;
    @Resource
    CloudifyCommandGenerator commandGen;

    @Resource
    private TestsUtils testsUtils;
    private List<Class<?>> indiceClassesToClean;

    public FunctionProcessorrTest() {
        indiceClassesToClean = Lists.newArrayList();
        indiceClassesToClean.add(ApplicationEnvironment.class);
        indiceClassesToClean.add(ApplicationVersion.class);
        indiceClassesToClean.add(DeploymentSetup.class);
        indiceClassesToClean.add(Application.class);
        indiceClassesToClean.add(Csar.class);
        indiceClassesToClean.add(Topology.class);
        indiceClassesToClean.add(PluginConfiguration.class);
        indiceClassesToClean.add(Deployment.class);
    }

    @BeforeClass
    public static void befaoreClass() {
        TestsUtils.cleanAlienTargetDir();
    }

    @Before
    public void before() throws Throwable {
        testsUtils.cleanESFiles(indiceClassesToClean);
        testsUtils.uploadCsar("tosca-normative-types", "1.0.0.wd03-SNAPSHOT");
        testsUtils.uploadCsar("alien-base-types", "1.0");
        testsUtils.uploadCsar("test-types", "1.0-SNAPSHOT");
        testsUtils.uploadCsar("tomcat-test-types", "1.0-SNAPSHOT");

    }

    @Test
    public void scalarParamSucessTest() throws Throwable {
        String computeName = "comp_tomcat_war";
        Topology topology = testsUtils.parseYamlTopology("badFunctionsTomcatWar");
        Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates = treeBuilder.buildPaaSNodeTemplate(topology);
        treeBuilder.getHostedOnTree(builtPaaSNodeTemplates);
        PaaSNodeTemplate computePaaS = builtPaaSNodeTemplates.get(computeName);
        Operation configOp = computePaaS.getIndexedNodeType().getInterfaces().get(PlanGeneratorConstants.NODE_LIFECYCLE_INTERFACE_NAME).getOperations()
                .get(PlanGeneratorConstants.CONFIGURE_OPERATION_NAME);
        IOperationParameter param = configOp.getInputParameters().get("testScalar");

        Assert.assertEquals("test", processor.evaluateParam((AbstractPropertyValue) param, computePaaS, builtPaaSNodeTemplates));
    }

    @Test
    public void getPropertySELFKeywordSucessTest() throws Throwable {

        String computeName = "comp_tomcat_war";
        Topology topology = testsUtils.parseYamlTopology("badFunctionsTomcatWar");
        Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates = treeBuilder.buildPaaSNodeTemplate(topology);
        treeBuilder.getHostedOnTree(builtPaaSNodeTemplates);
        PaaSNodeTemplate computePaaS = builtPaaSNodeTemplates.get(computeName);
        Operation configOp = computePaaS.getIndexedNodeType().getInterfaces().get(PlanGeneratorConstants.NODE_LIFECYCLE_INTERFACE_NAME).getOperations()
                .get(PlanGeneratorConstants.CONFIGURE_OPERATION_NAME);
        IOperationParameter param = configOp.getInputParameters().get("customHostName");

        Assert.assertEquals(computePaaS.getNodeTemplate().getProperties().get("customHostName"),
                processor.evaluateParam((AbstractPropertyValue) param, computePaaS, builtPaaSNodeTemplates));
    }

    @Test
    public void getPropertySOURCEAndTARGETKeywordsSucessTest() throws Throwable {

        String warName = "war_1";
        String tomcatName = "tomcat";
        Topology topology = testsUtils.parseYamlTopology("badFunctionsTomcatWar");
        Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates = treeBuilder.buildPaaSNodeTemplate(topology);
        treeBuilder.getHostedOnTree(builtPaaSNodeTemplates);
        PaaSNodeTemplate warPaaS = builtPaaSNodeTemplates.get(warName);
        PaaSNodeTemplate tomcatPaaS = builtPaaSNodeTemplates.get(tomcatName);
        PaaSRelationshipTemplate hostedOnRelTemp = warPaaS.getRelationshipTemplate("hostedOnTomcat");

        Operation configOp = hostedOnRelTemp.getIndexedRelationshipType().getInterfaces().get(PlanGeneratorConstants.RELATIONSHIP_LIFECYCLE_INTERFACE_NAME)
                .getOperations().get(PlanGeneratorConstants.POST_CONFIGURE_SOURCE);

        // test SOURCE keyword
        IOperationParameter param = configOp.getInputParameters().get("contextPath");
        Assert.assertEquals(warPaaS.getNodeTemplate().getProperties().get("contextPath"),
                processor.evaluateParam((AbstractPropertyValue) param, hostedOnRelTemp, builtPaaSNodeTemplates));

        // test TARGET keyword
        param = configOp.getInputParameters().get("tomcatVersion");
        Assert.assertEquals(tomcatPaaS.getNodeTemplate().getProperties().get("version"),
                processor.evaluateParam((AbstractPropertyValue) param, hostedOnRelTemp, builtPaaSNodeTemplates));

    }

    @Test(expected = BadUsageKeywordException.class)
    public void getPropertyWrongDefOrUSageTest() throws Throwable {

        String computeName = "comp_tomcat_war";
        Topology topology = testsUtils.parseYamlTopology("badFunctionsTomcatWar");
        Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates = treeBuilder.buildPaaSNodeTemplate(topology);
        treeBuilder.getHostedOnTree(builtPaaSNodeTemplates);
        PaaSNodeTemplate computePaaS = builtPaaSNodeTemplates.get(computeName);
        Operation configOp = computePaaS.getIndexedNodeType().getInterfaces().get(PlanGeneratorConstants.NODE_LIFECYCLE_INTERFACE_NAME).getOperations()
                .get(PlanGeneratorConstants.CONFIGURE_OPERATION_NAME);

        // case insufficient params for the function prop
        IOperationParameter param = configOp.getInputParameters().get("insufficientParams");
        Assert.assertNull(processor.evaluateParam((AbstractPropertyValue) param, computePaaS, builtPaaSNodeTemplates));

        // case keyword SOURCE used on a NodeType
        param = configOp.getInputParameters().get("keywordSourceBadUsage");
        try {
            processor.evaluateParam((AbstractPropertyValue) param, computePaaS, builtPaaSNodeTemplates);
        } catch (BadUsageKeywordException e) {
            // case keyword TARGET used on a NodeType
            param = configOp.getInputParameters().get("KeywordTargetBadUsage");
            processor.evaluateParam((AbstractPropertyValue) param, computePaaS, builtPaaSNodeTemplates);
        }

    }

    @Test
    public void getAttributeSOURCEAndTARGETKeywordsSucessTest() throws Throwable {

        String warName = "war_1";
        String computeName = "comp_tomcat_war";
        Topology topology = testsUtils.parseYamlTopology("badFunctionsTomcatWar");
        Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates = treeBuilder.buildPaaSNodeTemplate(topology);
        treeBuilder.getHostedOnTree(builtPaaSNodeTemplates);
        PaaSNodeTemplate warPaaS = builtPaaSNodeTemplates.get(warName);
        PaaSRelationshipTemplate hostedOnRelTemp = warPaaS.getRelationshipTemplate("hostedOnTomcat");

        Operation configOp = hostedOnRelTemp.getIndexedRelationshipType().getInterfaces().get(PlanGeneratorConstants.RELATIONSHIP_LIFECYCLE_INTERFACE_NAME)
                .getOperations().get(PlanGeneratorConstants.POST_CONFIGURE_SOURCE);

        // test SOURCE keyword
        String expected = String.format(GET_INSTANCE_ATTRIBUTE_FORMAT, "\"" + computeName + "\"", null, "\"warNodeContext\"");
        IOperationParameter param = configOp.getInputParameters().get("warNodeContext");
        Assert.assertEquals(expected, processor.evaluateParam((AbstractPropertyValue) param, hostedOnRelTemp, builtPaaSNodeTemplates));

        // test TARGET keyword
        expected = String.format(GET_IP_FORMAT, "\"" + computeName + "\"", null);
        param = configOp.getInputParameters().get("tomcatIp");
        Assert.assertEquals(expected, processor.evaluateParam((AbstractPropertyValue) param, hostedOnRelTemp, builtPaaSNodeTemplates));

    }

}
