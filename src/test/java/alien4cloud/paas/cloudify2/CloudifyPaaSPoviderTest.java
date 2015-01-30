package alien4cloud.paas.cloudify2;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.model.components.IndexedNodeType;
import alien4cloud.model.components.PropertyDefinition;
import alien4cloud.model.topology.NodeTemplate;
import alien4cloud.paas.cloudify2.generator.CommandGenerator;
import alien4cloud.paas.cloudify2.matcher.StorageTemplateMatcher;
import alien4cloud.paas.cloudify2.testutils.TestsUtils;
import alien4cloud.paas.exception.ResourceMatchingFailedException;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.tosca.normative.NormativeBlockStorageConstants;
import alien4cloud.utils.MapUtil;

import com.google.common.collect.Lists;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:mock-context-test.xml")
public class CloudifyPaaSPoviderTest {

    @Resource
    private TestsUtils elasticSearchUtils;

    @Resource(name = "cloudify-paas-provider-bean")
    private CloudifyPaaSProvider cloudifyPaaSPovider;

    @Resource
    private CommandGenerator generator;

    @Before
    public void beforeClass() {
        cleanAlienFiles();
    }

    private static void cleanAlienFiles() {
        FileUtils.deleteQuietly(new File("target/alien"));
    }

    @Test
    public void loopedGroovyCommand() throws IOException {
        String first = "while(!CloudifyExecutorUtils.executeGroovy(context, \"totototot/titit\", null)){\n\t  \n}";
        String second = "while(true){\n\t CloudifyExecutorUtils.executeGroovy(context, \"totototot/titit\", [\"ha\":\"ho\"]) \n}";
        String third = "while(true){\n\t CloudifyExecutorUtils.executeGroovy(context, \"totototot/titit\", [\"hi\":\"hu\", \"ha\":ho]) \n}";
        assertEquals(first, generator.getLoopedGroovyCommand(null, "!" + generator.getGroovyCommand("totototot/titit", null, null)));
        assertEquals(
                second,
                generator.getLoopedGroovyCommand(
                        generator.getGroovyCommand("totototot/titit", null, MapUtil.newHashMap(new String[] { "ha" }, new String[] { "ho" })), "true"));
        assertEquals(
                third,
                generator.getLoopedGroovyCommand(
                        generator.getGroovyCommand("totototot/titit", MapUtil.newHashMap(new String[] { "ha" }, new String[] { "ho" }),
                                MapUtil.newHashMap(new String[] { "hi" }, new String[] { "hu" })), "true"));
    }

    @SuppressWarnings("unchecked")
    @Test(expected = ResourceMatchingFailedException.class)
    public void storageTemplateMatcherFailTest() {
        StorageTemplateMatcher storageTemplateMatcher = new StorageTemplateMatcher();
        PaaSNodeTemplate nodeTemp = Mockito.mock(PaaSNodeTemplate.class);
        Mockito.when(nodeTemp.getId()).thenReturn("mock1");
        try {
            storageTemplateMatcher.getTemplate(nodeTemp);
        } catch (ResourceMatchingFailedException e) {
            List<StorageTemplate> lists = Lists.newArrayList();
            storageTemplateMatcher.configure(lists);
            try {
                storageTemplateMatcher.getTemplate(nodeTemp);
            } catch (ResourceMatchingFailedException e1) {
                Mockito.when(nodeTemp.getIndexedToscaElement()).thenReturn(Mockito.mock(IndexedNodeType.class));
                storageTemplateMatcher.getTemplate(nodeTemp);
            }
        }
    }

    @Test()
    public void storageTemplateTest() {
        StorageTemplateMatcher storageTemplateMatcher = new StorageTemplateMatcher();
        List<StorageTemplate> tempList = Lists.newArrayList(new StorageTemplate("SMALL_BLOCK", 1, "ext4"), new StorageTemplate("MEDIUM_BLOCK", 2, "ext4"));
        storageTemplateMatcher.configure(tempList);
        String storageTemp = storageTemplateMatcher.getDefaultTemplate();
        assertEquals("SMALL_BLOCK", storageTemp);
        PaaSNodeTemplate paasNodeTemp = Mockito.mock(PaaSNodeTemplate.class);
        Mockito.when(paasNodeTemp.getId()).thenReturn("mock1");
        IndexedNodeType nodeType = Mockito.mock(IndexedNodeType.class);
        NodeTemplate nodeTemplate = Mockito.mock(NodeTemplate.class);
        Mockito.when(nodeTemplate.getProperties()).thenReturn(MapUtil.newHashMap(new String[] { NormativeBlockStorageConstants.SIZE }, new String[] { "1" }));
        Mockito.when(nodeType.getElementId()).thenReturn(NormativeBlockStorageConstants.BLOCKSTORAGE_TYPE);
        Mockito.when(paasNodeTemp.getIndexedToscaElement()).thenReturn(nodeType);
        Mockito.when(paasNodeTemp.getNodeTemplate()).thenReturn(nodeTemplate);
        storageTemp = storageTemplateMatcher.getTemplate(paasNodeTemp);
        assertEquals("SMALL_BLOCK", storageTemp);
    }

    @Test
    public void deploymentPropertiesMapTest() {
        Map<String, PropertyDefinition> properties = cloudifyPaaSPovider.getDeploymentPropertyMap();
        Assert.assertNotNull(properties);
        assertEquals(2, properties.size());
        PropertyDefinition prop = properties.get(DeploymentPropertiesNames.STARTDETECTION_TIMEOUT_INSECOND);
        Assert.assertNotNull(prop);
        assertEquals("600", prop.getDefault());

        prop = properties.get(DeploymentPropertiesNames.DISABLE_SELF_HEALING);
        Assert.assertNotNull(prop);
        assertEquals("false", prop.getDefault());
    }

}
