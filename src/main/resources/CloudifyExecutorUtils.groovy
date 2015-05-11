import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import groovy.lang.Binding
import groovy.transform.Synchronized

import org.cloudifysource.utilitydomain.context.ServiceContextFactory

public class CloudifyExecutorUtils {

    static def counter = new AtomicInteger()
    static def threadPool = Executors.newFixedThreadPool(50, { r -> return new Thread(r as Runnable, "alien-executor-" + counter.incrementAndGet()) } as ThreadFactory )
    static def call = { c -> threadPool.submit(c as Callable) }
    
    static List startStates = ["initial", "created", "configured", "started", "available"];
    static List shutdownStates = ["stopped", "deleted"];
    static def DEFAULT_LEASE = 1000 * 60 * 60
    


    /**
     * execute a script bash or batch
     * 
     * @param script
     * @param argsMap
     * @return
     */
    static def executeScript(context, script, Map argsMap) {

        // Execute bash script
        def serviceDirectory = context.getServiceDirectory()
        println "service dir is: ${serviceDirectory}; script is: ${script}"
        def fullPathScript = "${serviceDirectory}/${script}";
        new AntBuilder().sequential {
            echo(message: "${fullPathScript} will be mark as executable...")
            chmod(file: "${fullPathScript}", perm:"+xr")
        }

        String[] environment = new EnvironmentBuilder().buildShOrBatchEnvironment(argsMap);

        println "Executing file ${serviceDirectory}/${script}.\n environment is: ${environment}"
        def scriptProcess = "${serviceDirectory}/${script}".execute(environment, null)
        //scriptProcess.consumeProcessOutput(System.out, System.out)
        def myOutputListener = new ProcessOutputListener()
        
        scriptProcess.consumeProcessOutputStream(myOutputListener)
        scriptProcess.consumeProcessErrorStream(System.out)

        scriptProcess.waitFor()
        def scriptExitValue = scriptProcess.exitValue()

        print """
      ----------${script} : bash : Return Code ----------
      Return Code : $scriptExitValue
      ---------------------------------\n
      """
        if(scriptExitValue) {
            throw new RuntimeException("Error executing the script ${script} (return code: $scriptExitValue)")
        } else {
            return myOutputListener.getLastOutput()
        }
    }

    //
    static class ProcessOutputListener implements Appendable {
    
      private StringWriter outputBufffer = new StringWriter();
      
      Appendable append(char c) throws IOException {
        System.out.append(c)
        outputBufffer.append(c);
        return this
      }
      
      Appendable append(CharSequence csq, int start, int end) throws IOException {
        System.out.append(csq, start, end)
        outputBufffer.append(csq, start, end)
        return this
      }
      
      Appendable append(CharSequence csq) throws IOException {
        System.out.append(csq)
        outputBufffer.append(csq)
        return this
      }
    
      String getLastOutput() {
        outputBufffer.flush()
        def outputString = outputBufffer.toString();
        if (outputString == null || outputString.size() == 0) {
          return null;
        }
        def lineList = outputString.readLines();
        return lineList[lineList.size() -1]
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

    static def fireEvent(nodeId, event, lease) {
        def context = ServiceContextFactory.getServiceContext()

        // Fire event
        def application = context.getApplicationName()
        def instanceId = context.getInstanceId()
        def eventResume = [
            event: event,
            lease: getEventLeaseInMillis(lease)
        ]
        println "THE INSTANCE ID is <${instanceId}>"
        CloudifyUtils.putEvent(application, nodeId, instanceId, eventResume)
    }

    static def fireBlockStorageEvent(nodeId, event, volumeId, lease) {
        def context = ServiceContextFactory.getServiceContext()

        // Fire event
        def application = context.getApplicationName()
        def instanceId = context.getInstanceId()
        def eventResume = [
            event: event,
            lease: getEventLeaseInMillis(lease)
        ]
        println "THE INSTANCE ID is <${instanceId}>"
        CloudifyUtils.putBlockStorageEvent(application, nodeId, instanceId, eventResume, volumeId)
    }

    static def fireRelationshipEvent(nodeId, relationshipId, event, associatedNodeId, associatedNodeService, command, lease) {
        def context = ServiceContextFactory.getServiceContext()
        def sourceService = null;
        def targetService = null;
        def sourceId = null;
        def targetId = null;

        switch(event) {
            case "add_source":
            case "remove_source":
                sourceService =  context.getServiceName();
                targetService = associatedNodeService;
                sourceId = nodeId;
                targetId = associatedNodeId;
                break;
            case "add_target":
            case "remove_target":
                sourceService =  associatedNodeService;
                targetService = context.getServiceName();
                sourceId = associatedNodeId;
                targetId = nodeId;
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
            commandName: command,
            lease: getEventLeaseInMillis(lease)
        ]
        def nodeResume = [
            id : nodeId,
            instanceId: context.getInstanceId(),
            ip_address: context.getPrivateAddress()
        ]
        
        // Fire event
        def application = context.getApplicationName()
        def ip_address = context.getPrivateAddress()
        println "THE INSTANCE ID is <${nodeResume.instanceId}>"
        CloudifyUtils.putRelationshipOperationEvent(application, nodeResume, eventResume, source, target)
    }

    static def waitFor(cloudifyService, nodeId, status) {
        List validStates = getValidStates(status);
        if(validStates!=null) {
            CloudifyUtils.waitFor(cloudifyService, nodeId, validStates)
        }
    }


    static def isNodeStarted(context, nodeToCheck) {
        List validStates =  getValidStates("started")
        def state = CloudifyUtils.getState(context.getApplicationName(), nodeToCheck, context.getInstanceId())
        println "Got Last state for ${nodeToCheck}: ${state}";
        return validStates.contains(state);
    }

    static def shutdown() {
        println "Shutting down threadpool"
        threadPool.shutdownNow();
        println "threadpool shut down!"
    }
    
    /**
     * get all the states after a specific one.
     * 
     * For ex, if the provided state is configured, then started and available are valids. 
     * 
     * @param state
     * @return
     */
    static private getValidStates(String state) {
        if(startStates.contains(state) ) {
            int index =startStates.indexOf(state); 
            return startStates.subList(index, startStates.size())   
        }else if(shutdownStates.contains(state)) {
            int index = shutdownStates.indexOf(state)
            return shutdownStates.subList(index, shutdownStates.size())
        } 
        return null;
    }
    
    static private getEventLeaseInMillis(lease) {
       return lease ? Math.round(lease * 60 * 60 * 1000) : DEFAULT_LEASE;
    } 

}