import java.util.concurrent.*

import groovy.lang.Binding;
// import java.util.concurrent.TimeUnit
import groovy.transform.Synchronized

import org.cloudifysource.utilitydomain.context.ServiceContextFactory

public class CloudifyExecutorUtils {
  static def threadPool = Executors.newFixedThreadPool(50)
  static def call = { c -> threadPool.submit(c as Callable) }

  static def executeBash(bashScript) {
    // Execute bash script
    def context = ServiceContextFactory.getServiceContext()

    def serviceDirectory = context.getServiceDirectory()
    println "service dir is: ${serviceDirectory}; script is: ${bashScript}"
    def fullPathScript = "${serviceDirectory}/${bashScript}";
     new AntBuilder().sequential {
      echo(message: "${fullPathScript} will be mark as executable...")
      chmod(file: "${fullPathScript}", perm:"+xr")
    }
    def scriptProcess = "${serviceDirectory}/${bashScript}".execute()
    def scriptErr = new StringBuffer()
    def scriptOut = new StringBuffer()
    scriptProcess.consumeProcessOutput(scriptOut, scriptErr)

    def scriptExitValue = scriptProcess.waitFor()

    if(scriptErr) {
      print """
      ---------- bash:stderr ----------
      Return Code : $scriptExitValue
      $scriptErr
      ---------------------------------
      """
    }
    if(scriptOut) {
      print """
      ---------- bash:stdout ----------
      $scriptOut
      ---------------------------------
      """
    }

    scriptExitValue = scriptProcess.exitValue()
    if(scriptExitValue) {
      throw new RuntimeException("Error executing the script ${bashScript} (return code: $scriptExitValue)")
    }
  }

  static def executeGroovy(groovyScript, String... args) {
      // Execute groovy script
      def context = ServiceContextFactory.getServiceContext()
      def serviceDirectory = context.getServiceDirectory()
      
      if(args == null) {
          def shell = new GroovyShell()
          return shell.evaluate(new File("${serviceDirectory}/${groovyScript}"))
      } else {
          Binding argBinding = new Binding(args)
          def shell = new GroovyShell(argBinding)
          return shell.evaluate(new File("${serviceDirectory}/${groovyScript}"))
      }
  }
  
  /**
   * for a closure, we should not use the ServiceContextFactory as the context instance is already injected in the service file
   * */
  static def executeGroovyInClosure(context, groovyScript,  String... args) {
    // Execute groovy script
    def serviceDirectory = context.getServiceDirectory()
      Binding argBinding = args == null ? new Binding() : new Binding(args)
      argBinding.setVariable("context", context)
      //hack for a MissingPropertyException thrown for CloudifyExecutorUtils
      argBinding.setVariable("CloudifyExecutorUtils", this)
      def shell = new GroovyShell(argBinding)
      return shell.evaluate(new File("${serviceDirectory}/${groovyScript}"))
  }

  static def executeParallel(groovyScripts, bashScripts) {
    def executionList = []
    println "$groovyScripts"
    for(script in groovyScripts) {
      println "parallel launch is $script"
      def theScript = script
      executionList.add(call{ executeGroovy(theScript) })
    }
    for(script in bashScripts) {
      def theScript = script
      executionList.add(call{ executeBash(theScript) })
    }
    for(execution in executionList) {
      execution.get();
    }
  }
  
  static def executeAsync(groovyScripts, bashScripts) {
      def executionList = []
      println "$groovyScripts"
      for(script in groovyScripts) {
        println "asynchronous launch is $script"
        def theScript = script
        executionList.add(call{ executeGroovy(theScript) })
      }
      for(script in bashScripts) {
        def theScript = script
        executionList.add(call{ executeBash(theScript) })
      }
    }

  static def fireEvent(nodeId, status) {
    def context = ServiceContextFactory.getServiceContext()

    // Fire event
    def application = context.getApplicationName()
    def instanceId = context.getInstanceId()
    println "THE INSTANCE ID is <${instanceId}>"
    CloudifyUtils.putEvent(application, nodeId, instanceId, status)
  }
  
  static def fireBlockStorageEvent(nodeId, event, volumeId) {
    def context = ServiceContextFactory.getServiceContext()

    // Fire event
    def application = context.getApplicationName()
    def instanceId = context.getInstanceId()
    println "THE INSTANCE ID is <${instanceId}>"
    CloudifyUtils.putBlockStorageEvent(application, nodeId, instanceId, event, volumeId)
  }

  static def waitFor(cloudifyService, nodeId, status) {
    CloudifyUtils.waitFor(cloudifyService, nodeId, status)
  }
  
  static def shutdown() {
      threadPool.shutdownNow();
  }
  
}