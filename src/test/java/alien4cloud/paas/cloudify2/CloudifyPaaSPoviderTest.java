package alien4cloud.paas.cloudify2;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import alien4cloud.model.components.PropertyDefinition;
import alien4cloud.paas.cloudify2.generator.CommandGenerator;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:mock-context-test.xml")
public class CloudifyPaaSPoviderTest {

    @Resource(name = "cloudify-paas-provider")
    private CloudifyPaaSProviderFactory cloudifyPaaSProvider;

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
    public void deploymentPropertiesMapTest() {
        Map<String, PropertyDefinition> properties = cloudifyPaaSProvider.getDeploymentPropertyDefinitions();
        Assert.assertNotNull(properties);
        assertEquals(4, properties.size());
        PropertyDefinition prop = properties.get(DeploymentPropertiesNames.STARTDETECTION_TIMEOUT_INSECOND);
        Assert.assertNotNull(prop);
        assertEquals("600", prop.getDefault());

        prop = properties.get(DeploymentPropertiesNames.DISABLE_SELF_HEALING);
        Assert.assertNotNull(prop);
        assertEquals("false", prop.getDefault());
    }

}
