import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import groovy.lang.Binding
import groovy.transform.Synchronized

import org.cloudifysource.utilitydomain.context.ServiceContextFactory

public class CloudifyExecutorUtils {

    static def counter = new AtomicInteger()
    static def threadPool = Executors.newFixedThreadPool(50, { r -> return new Thread(r as Runnable, "alien-executor-" + counter.incrementAndGet()) } as ThreadFactory )
    static def call = { c -> threadPool.submit(c as Callable) }


    /**
     * execute a script bash or batch
     * 
     * @param script
     * @param argsMap
     * @return
     */
    static def executeScript(script, Map argsMap) {

        // Execute bash script
        def context = ServiceContextFactory.getServiceContext()
        def serviceDirectory = context.getServiceDirectory()
        println "service dir is: ${serviceDirectory}; script is: ${script}"
        def fullPathScript = "${serviceDirectory}/${script}";
        new AntBuilder().sequential {
            echo(message: "${fullPathScript} will be mark as executable...")
            chmod(file: "${fullPathScript}", perm:"+xr")
        }

        String[] environment = new EnvironmentBuilder().buildShOrBatchEnvironment(argsMap);

        println "Executing file ${serviceDirectory}/${script}.\n environment is: ${script}"
        def scriptProcess = "${serviceDirectory}/${script}".execute(environment, null)
        scriptProcess.consumeProcessOutput(System.out, System.out)

        scriptProcess.waitFor()
        def scriptExitValue = scriptProcess.exitValue()

        print """
      ----------${script} : bash : Return Code ----------
      Return Code : $scriptExitValue
      ---------------------------------\n
      """
        if(scriptExitValue) {
            throw new RuntimeException("Error executing the script ${script} (return code: $scriptExitValue)")
        }
    }


    /**
     * Execute a goovy script. Argument are passed to the groovy via the GroovyShell Binding, where they are injected as variables.
     * Consider to pass them as env vars
     * for a closure, we should not use the ServiceContextFactory as the context instance is already injected in the service file
     * Therefore, the caller script should have a defined "context" variable
     * */
    static def executeGroovy(context, groovyScript,  Map argsMap) {
        def serviceDirectory = context.getServiceDirectory()

        Binding binding = new EnvironmentBuilder().buildGroovyEnvironment(context, argsMap)

        //hack for a MissingPropertyException thrown for CloudifyExecutorUtils
        binding.setVariable("CloudifyExecutorUtils", this)
        def shell = new GroovyShell(CloudifyExecutorUtils.class.classLoader,binding)
        println "Evaluating file ${serviceDirectory}/${groovyScript}.\n environment is: ${argsMap}"
        return shell.evaluate(new File("${serviceDirectory}/${groovyScript}"))
    }

    static def executeParallel(groovyScripts, otherScripts) {
        def executionList = []
        println "$groovyScripts"
        for(script in groovyScripts) {
            println "parallel launch is $script"
            def theScript = script
            executionList.add(call{ executeGroovy(ServiceContextFactory.getServiceContext(), theScript, null) })
        }
        for(script in otherScripts) {
            def theScript = script
            executionList.add(call{ executeScript(theScript, null) })
        }
        for(execution in executionList) {
            execution.get();
        }
    }

    static def executeAsync(groovyScripts, otherScripts) {
        def executionList = []
        println "$groovyScripts"
        for(script in groovyScripts) {
            println "asynchronous launch is ${script}"
            def theScript = script
            executionList.add(call{ executeGroovy(ServiceContextFactory.getServiceContext(), theScript, null) })
        }
        for(script in otherScripts) {
            def theScript = script
            executionList.add(call{ executeScript(theScript, null) })
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

    static def fireRelationshipEvent(nodeId, relationshipId, event, associatedNodeId, associatedNodeService, command) {
        def context = ServiceContextFactory.getServiceContext()
        def sourceService = null;
        def targetService = null;
        def sourceId = null;
        def targetId = null;

        switch(event) {
            case "add_target":
                sourceService =  associatedNodeService;
                targetService = context.getServiceName();
                sourceId = associatedNodeId;
                targetId = nodeId;
                break;
            case "add_source":
                sourceService =  context.getServiceName();
                targetService = associatedNodeService;
                sourceId = nodeId;
                targetId = associatedNodeId;
                break;
            default:
                return;
        }

        def source = [
            name: sourceId,
            service:  sourceService
        ];
        def target = [
            name: targetId,
            service:  targetService
        ];

        def eventResume = [
            relationshipId: relationshipId,
            event: event,
            commandName: command
        ]
        // Fire event
        def application = context.getApplicationName()
        def instanceId = context.getInstanceId()
        println "THE INSTANCE ID is <${instanceId}>"
        CloudifyUtils.putRelationshipOperationEvent(application, nodeId, instanceId, eventResume, source, target)
    }

    static def waitFor(cloudifyService, nodeId, status) {
        CloudifyUtils.waitFor(cloudifyService, nodeId, status)
    }


    static def isNodeStarted(context, cloudifyService, nodeToCheck) {
        def lastEvent = CloudifyUtils.getLastEvent(context.getApplicationName(), cloudifyService, nodeToCheck, context.getInstanceId())
        println "Got Last event for ${nodeToCheck}: ${lastEvent}";
        return lastEvent == "started" || lastEvent == "available";
    }

    static def shutdown() {
        println "Shutting down threadpool"
        threadPool.shutdownNow();
        println "threadpool shut down!"
    }

}