package alien4cloud.paas.cloudify2.funtion;

import java.io.IOException;
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
import alien4cloud.model.components.AbstractPropertyValue;
import alien4cloud.model.components.Csar;
import alien4cloud.model.components.FunctionPropertyValue;
import alien4cloud.model.components.IValue;
import alien4cloud.model.components.IndexedToscaElement;
import alien4cloud.model.components.Operation;
import alien4cloud.model.deployment.Deployment;
import alien4cloud.model.topology.Topology;
import alien4cloud.paas.IPaaSTemplate;
import alien4cloud.paas.cloudify2.funtion.FunctionProcessor.IParamEvalResult;
import alien4cloud.paas.cloudify2.testutils.TestsUtils;
import alien4cloud.paas.function.BadUsageKeywordException;
import alien4cloud.paas.function.FunctionEvaluator;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.paas.model.PaaSRelationshipTemplate;
import alien4cloud.paas.plan.TopologyTreeBuilderService;
import alien4cloud.paas.plan.ToscaNodeLifecycleConstants;
import alien4cloud.paas.plan.ToscaRelationshipLifecycleConstants;
import alien4cloud.plugin.model.PluginConfiguration;

import com.google.common.collect.Lists;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context-testit.xml")
public class FunctionProcessorTest {

    private static final String GET_INSTANCE_ATTRIBUTE_FORMAT = "CloudifyAttributesUtils.getAttribute(context, %s, %s, %s)";
    private static final String GET_IP_FORMAT = "CloudifyAttributesUtils.getIp(context, %s, %s)";

    @Resource
    private FunctionProcessor processor;

    @Resource
    private TopologyTreeBuilderService treeBuilder;

    @Resource
    private TestsUtils testsUtils;
    private List<Class<?>> indiceClassesToClean;

    public FunctionProcessorTest() {
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
        testsUtils.uploadGitArchive("tosca-normative-types-1.0.0.wd03", "");
        testsUtils.uploadGitArchive("alien-extended-types", "alien-base-types-1.0-SNAPSHOT");
        testsUtils.uploadGitArchive("samples", "tomcat-war");
        testsUtils.uploadArchive("test-types-1.0-SNAPSHOT");
    }

    @Test
    public void scalarParamSucessTest() throws Throwable {
        String computeName = "comp-tomcat-war";
        Topology topology = testsUtils.parseYamlTopology("functionsKeywordsTest");
        Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates = treeBuilder.buildPaaSTopology(topology).getAllNodes();
        PaaSNodeTemplate computePaaS = builtPaaSNodeTemplates.get(computeName);
        Operation configOp = computePaaS.getIndexedToscaElement().getInterfaces().get(ToscaNodeLifecycleConstants.STANDARD).getOperations()
                .get(ToscaNodeLifecycleConstants.CONFIGURE);
        IValue param = configOp.getInputParameters().get("testScalar");

        Assert.assertEquals("test", evaluateParam((AbstractPropertyValue) param, computePaaS, builtPaaSNodeTemplates));
    }

    @Test
    public void getPropertyKeywordsOnNodeTemplateSucessTest() throws Throwable {

        String computeName = "comp-tomcat-war";
        Topology topology = testsUtils.parseYamlTopology("functionsKeywordsTest");
        Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates = treeBuilder.buildPaaSTopology(topology).getAllNodes();
        PaaSNodeTemplate computePaaS = builtPaaSNodeTemplates.get(computeName);
        Operation configOp = computePaaS.getIndexedToscaElement().getInterfaces().get(ToscaNodeLifecycleConstants.STANDARD).getOperations()
                .get(ToscaNodeLifecycleConstants.CONFIGURE);
        IValue param = configOp.getInputParameters().get("customHostName");

        Assert.assertEquals(FunctionEvaluator.getScalarValue(computePaaS.getNodeTemplate().getProperties().get("customHostName")),
                evaluateParam((AbstractPropertyValue) param, computePaaS, builtPaaSNodeTemplates));

        // HOST keyword
        String warName = "war_1";
        PaaSNodeTemplate warPaaS = builtPaaSNodeTemplates.get(warName);
        PaaSNodeTemplate tomcatPaaS = builtPaaSNodeTemplates.get("tomcat");
        Operation customHelloOp = warPaaS.getIndexedToscaElement().getInterfaces().get("custom").getOperations().get("update_war_file");
        param = customHelloOp.getInputParameters().get("TOMCAT_HOME");
        Assert.assertEquals(FunctionEvaluator.getScalarValue(tomcatPaaS.getNodeTemplate().getProperties().get("tomcat_home")),
                evaluateParam((AbstractPropertyValue) param, warPaaS, builtPaaSNodeTemplates));
    }

    @Test
    public void getPropertyKeywordsOnRelqtionshipSucessTest() throws Throwable {

        String warName = "war_1";
        String warName_2 = "war_2";
        String tomcatName = "tomcat";
        Topology topology = testsUtils.parseYamlTopology("functionsKeywordsTest");
        Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates = treeBuilder.buildPaaSTopology(topology).getAllNodes();
        PaaSNodeTemplate warPaaS = builtPaaSNodeTemplates.get(warName);
        PaaSNodeTemplate warPaaS_2 = builtPaaSNodeTemplates.get(warName_2);
        PaaSNodeTemplate tomcatPaaS = builtPaaSNodeTemplates.get(tomcatName);
        PaaSRelationshipTemplate hostedOnRelTemp = warPaaS.getRelationshipTemplate("hostedOnTomcat", "war_1");
        PaaSRelationshipTemplate hostedOnRelTemp_2 = warPaaS_2.getRelationshipTemplate("hostedOnTomcat", "war_2");

        Operation configOp = hostedOnRelTemp.getIndexedToscaElement().getInterfaces().get(ToscaRelationshipLifecycleConstants.CONFIGURE).getOperations()
                .get(ToscaRelationshipLifecycleConstants.POST_CONFIGURE_SOURCE);

        // test SOURCE keyword
        IValue param = configOp.getInputParameters().get("contextPath");
        Assert.assertEquals(FunctionEvaluator.getScalarValue(warPaaS.getNodeTemplate().getProperties().get("context_path")),
                evaluateParam((AbstractPropertyValue) param, hostedOnRelTemp, builtPaaSNodeTemplates));

        // test TARGET keyword
        param = configOp.getInputParameters().get("tomcatVersion");
        Assert.assertEquals(FunctionEvaluator.getScalarValue(tomcatPaaS.getNodeTemplate().getProperties().get("version")),
                evaluateParam((AbstractPropertyValue) param, hostedOnRelTemp, builtPaaSNodeTemplates));

        // test SELF keyword on relationship
        param = configOp.getInputParameters().get("relName");
        Assert.assertEquals(FunctionEvaluator.getScalarValue(hostedOnRelTemp.getTemplate().getProperties().get("relName")),
                FunctionEvaluator.evaluateGetPropertyFunction((FunctionPropertyValue) param, hostedOnRelTemp, builtPaaSNodeTemplates));

        Assert.assertEquals(FunctionEvaluator.getScalarValue(hostedOnRelTemp_2.getTemplate().getProperties().get("relName")),
                FunctionEvaluator.evaluateGetPropertyFunction((FunctionPropertyValue) param, hostedOnRelTemp_2, builtPaaSNodeTemplates));

    }

    @Test(expected = BadUsageKeywordException.class)
    public void getPropertyWrongDefOrUSageTest() throws Throwable {

        String computeName = "comp-tomcat-war";
        Topology topology = testsUtils.parseYamlTopology("functionsKeywordsTest");
        Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates = treeBuilder.buildPaaSTopology(topology).getAllNodes();
        PaaSNodeTemplate computePaaS = builtPaaSNodeTemplates.get(computeName);
        Operation configOp = computePaaS.getIndexedToscaElement().getInterfaces().get(ToscaNodeLifecycleConstants.STANDARD).getOperations()
                .get(ToscaNodeLifecycleConstants.CONFIGURE);

        // case insufficient params for the function prop
        IValue param = configOp.getInputParameters().get("insufficientParams");
        Assert.assertNull(evaluateParam((AbstractPropertyValue) param, computePaaS, builtPaaSNodeTemplates));

        // case keyword SOURCE used on a NodeType
        param = configOp.getInputParameters().get("keywordSourceBadUsage");
        try {
            evaluateParam((AbstractPropertyValue) param, computePaaS, builtPaaSNodeTemplates);
        } catch (BadUsageKeywordException e) {
            // case keyword TARGET used on a NodeType
            param = configOp.getInputParameters().get("KeywordTargetBadUsage");
            evaluateParam((AbstractPropertyValue) param, computePaaS, builtPaaSNodeTemplates);
        }

    }

    @Test
    public void getAttributeKeywordsSucessTest() throws Throwable {

        String warName = "war_1";
        String computeName = "comp-tomcat-war";
        Topology topology = testsUtils.parseYamlTopology("functionsKeywordsTest");
        Map<String, PaaSNodeTemplate> builtPaaSNodeTemplates = treeBuilder.buildPaaSTopology(topology).getAllNodes();
        PaaSNodeTemplate warPaaS = builtPaaSNodeTemplates.get(warName);
        PaaSRelationshipTemplate hostedOnRelTemp = warPaaS.getRelationshipTemplate("hostedOnTomcat", "war_1");

        Operation configOp = hostedOnRelTemp.getIndexedToscaElement().getInterfaces().get(ToscaRelationshipLifecycleConstants.CONFIGURE).getOperations()
                .get(ToscaRelationshipLifecycleConstants.POST_CONFIGURE_SOURCE);

        // test SOURCE keyword
        String expected = String.format(GET_INSTANCE_ATTRIBUTE_FORMAT, "\"" + computeName + "\"", null, "\"warNodeContext\"");
        IValue param = configOp.getInputParameters().get("warNodeContext");
        Assert.assertEquals(expected, evaluateParam((AbstractPropertyValue) param, hostedOnRelTemp, builtPaaSNodeTemplates));

        // test TARGET keyword
        expected = String.format(GET_IP_FORMAT, "\"" + computeName + "\"", null);
        param = configOp.getInputParameters().get("tomcatIp");
        Assert.assertEquals(expected, evaluateParam((AbstractPropertyValue) param, hostedOnRelTemp, builtPaaSNodeTemplates));

    }

    private String evaluateParam(final AbstractPropertyValue param, final IPaaSTemplate<? extends IndexedToscaElement> basePaaSTemplate,
            final Map<String, PaaSNodeTemplate> builtPaaSTemplates) throws IOException {
        IParamEvalResult result = processor.evaluate(param, basePaaSTemplate, builtPaaSTemplates, null);
        return result.get();
    }

}
