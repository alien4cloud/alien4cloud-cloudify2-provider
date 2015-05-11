package alien4cloud.paas.cloudify2;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.io.File;

import org.junit.Ignore;
import org.junit.Test;

public class GroovySandbox {

    @Test
    @Ignore
    // this is more Sandbox
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
    // this is more Sandbox
    public void testClass() {

        try {
            GroovyClassLoader classLoader = new GroovyClassLoader();

            Class groovy = classLoader.parseClass(new File("src/test/resources/groovy/class.groovy"));
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

}
