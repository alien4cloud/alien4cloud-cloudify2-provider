package alien4cloud.paas.cloudify2;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.component.model.IndexedNodeType;
import alien4cloud.component.repository.exception.CSARVersionAlreadyExistsException;
import alien4cloud.paas.cloudify2.testutils.ElasticSearchUtils;
import alien4cloud.paas.exception.ResourceMatchingFailedException;
import alien4cloud.paas.model.PaaSNodeTemplate;
import alien4cloud.tosca.container.exception.CSARParsingException;
import alien4cloud.tosca.container.exception.CSARValidationException;
import alien4cloud.tosca.container.model.NormativeBlockStorageConstants;
import alien4cloud.tosca.container.model.topology.NodeTemplate;
import alien4cloud.utils.MapUtil;

import com.google.common.collect.Lists;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:mock-context-test.xml")
public class CloudifyPaaSPoviderTest {

    private static final String CSAR_SOURCE_PATH = "src/test/resources/csars/";

    @Resource
    private ElasticSearchUtils elasticSearchUtils;

    @Resource(name = "cloudify-paas-provider-bean")
    private CloudifyPaaSProvider cloudifyPaaSPovider;

    @Resource
    private CloudifyCommandGenerator generator;

    @Before
    public void beforeClass() {
        cleanAlienFiles();
    }

    private static void cleanAlienFiles() {
        FileUtils.deleteQuietly(new File("target/alien"));
    }

    public void initElasticSearch() throws IOException, CSARParsingException, CSARVersionAlreadyExistsException, CSARValidationException {
        elasticSearchUtils.uploadCSAR(CSAR_SOURCE_PATH + "tosca-base-types/1.0");
        elasticSearchUtils.uploadCSAR(CSAR_SOURCE_PATH + "apache-lb-types/0.1");
        elasticSearchUtils.uploadCSAR(CSAR_SOURCE_PATH + "tomcat-types/0.1");
        elasticSearchUtils.uploadCSAR(CSAR_SOURCE_PATH + "tomcatGroovy-types/0.1");
    }

    @Test
    public void loopedGroovyCommand() {
        String first = "while(!CloudifyExecutorUtils.executeGroovy(\"totototot/titit\", null)){\n\t  \n}";
        String second = "while(true){\n\t CloudifyExecutorUtils.executeGroovy(\"totototot/titit\", \"ha\") \n}";
        assertEquals(first, generator.getLoopedGroovyCommand(generator.getGroovyCommand("totototot/titit", (String[]) null), null));
        assertEquals(second, generator.getLoopedGroovyCommand(generator.getGroovyCommand("totototot/titit", "ha"), "true"));
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
            storageTemplateMatcher.configure(Mockito.mock(ArrayList.class));
            try {
                storageTemplateMatcher.getTemplate(nodeTemp);
            } catch (ResourceMatchingFailedException e1) {
                Mockito.when(nodeTemp.getIndexedNodeType()).thenReturn(Mockito.mock(IndexedNodeType.class));
                storageTemplateMatcher.getTemplate(nodeTemp);
            }
        }
    }

    @Test()
    public void storageTemplateTest() {
        StorageTemplateMatcher storageTemplateMatcher = new StorageTemplateMatcher();
        List<StorageTemplate> tempList = Lists.newArrayList(new StorageTemplate("SMALL_BLOCK", 1, "ext4"), new StorageTemplate("MEDIUM_BLOCK", 2, "ext4"));
        storageTemplateMatcher.configure(tempList);

        PaaSNodeTemplate paasNodeTemp = Mockito.mock(PaaSNodeTemplate.class);
        Mockito.when(paasNodeTemp.getId()).thenReturn("mock1");
        IndexedNodeType nodeType = Mockito.mock(IndexedNodeType.class);
        NodeTemplate nodeTemplate = Mockito.mock(NodeTemplate.class);
        Mockito.when(nodeTemplate.getProperties()).thenReturn(MapUtil.newHashMap(new String[] { NormativeBlockStorageConstants.SIZE }, new String[] { "1" }));
        Mockito.when(nodeType.getElementId()).thenReturn(NormativeBlockStorageConstants.BLOCKSTORAGE_TYPE);
        Mockito.when(paasNodeTemp.getIndexedNodeType()).thenReturn(nodeType);
        Mockito.when(paasNodeTemp.getNodeTemplate()).thenReturn(nodeTemplate);
        String storageTemp = storageTemplateMatcher.getTemplate(paasNodeTemp);

        assertEquals("SMALL_BLOCK", storageTemp);
    }

}
