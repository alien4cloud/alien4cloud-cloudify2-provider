package alien4cloud.paas.cloudify2;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;

public class GroovySandbox {

    @Test
    @Ignore
    // this is more a Sandbox than a test
    public void testScript() {

        try {
            GroovyShell shell = new GroovyShell();
            Script script = shell.parse(new File("src/test/resources/groovy/test.groovy"));

            Binding binding = new Binding();
            // Object[] path = { "C:\\music\\temp\\mp3s" };
            // binding.setVariable("args", path);
            script.setBinding(binding);

            script.run();

        } catch (Exception e) {
            System.out.println("error loading file");
        }
    }

    @Test
    @Ignore
    // this is more a Sandbox than a test
    public void testClass() {

        try {
            GroovyClassLoader classLoader = new GroovyClassLoader();

            Class groovy = classLoader.parseClass(new File("src/test/resources/groovy/class.groovy"));
            classLoader.close();
            GroovyObject groovyObj = (GroovyObject) groovy.newInstance();
            // groovyObj.invokeMethod("append", new Object[] { "totot1\n" });
            // groovyObj.invokeMethod("append", new Object[] { "tototo2" });
            Object result = groovyObj.invokeMethod("getLastOutput", new Object[] {});
            System.out.println("====");
            System.out.println("<" + result + ">");
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("error loading file");
        }
    }

    @Test
    @Ignore
    // this is more a Sandbox than a test
    public void testWrap() {

        try {
            GroovyClassLoader classLoader = new GroovyClassLoader();

            Class groovy = classLoader.parseClass(new File("src/test/resources/groovy/wrap.groovy"));
            classLoader.close();
            GroovyObject groovyObj = (GroovyObject) groovy.newInstance();
            // groovyObj.invokeMethod("append", new Object[] { "totot1\n" });
            // groovyObj.invokeMethod("append", new Object[] { "tototo2" });
            String scriptPath = "/home/developer/checkout/alien4cloud/branches/ALIEN-843-948/alien4cloud-cloudify2-provider/src/test/resources/data/scriptThatExports.sh";
            Map argsMap = new HashMap();
            // argsMap.put("EXPECTED_OUTPUTS", "OUTPUT1;OUTPUT2");
            List expectedOutputs = new ArrayList();
            expectedOutputs.add("OUTPUT1");
            expectedOutputs.add("OUTPUT2");
            expectedOutputs.add("OUTPUT3");
            Object result = groovyObj.invokeMethod("wrap", new Object[] { scriptPath, argsMap, expectedOutputs });
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("error loading file");
        }
    }

    @Test
    @Ignore
    // this is more a Sandbox than a test
    public void testWrapGroovy() {

        try {
            GroovyClassLoader classLoader = new GroovyClassLoader();

            Class groovy = classLoader.parseClass(new File("src/test/resources/groovy/wrap.groovy"));
            classLoader.close();
            GroovyObject groovyObj = (GroovyObject) groovy.newInstance();
            Object result = groovyObj.invokeMethod("wrapGroovy", new Object[] {});
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("error loading file");
        }
    }

}
