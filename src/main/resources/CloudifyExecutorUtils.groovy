import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.*
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
    static def OPERATION_FQN = "OPERATION_FQN";
    
    private static def SCRIPT_WRAPPER_NUX = "scriptWrapper.sh"
    private static def SCRIPT_WRAPPER_DOS = "scriptWrapper.bat"

    /**
     * execute a script bash or batch
     * 
     * @param script
     * @param argsMap
     * @param expectedOutputs
     * @return
     */
    static def executeScript(context, script, Map argsMap, List expectedOutputs) {
        def operationFQN = argsMap?argsMap.remove(OPERATION_FQN):null;
        def serviceDirectory = context.getServiceDirectory()
        
        def scripWrapper = "${SCRIPT_WRAPPER_NUX}"
        def fileExtension = script.substring(script.lastIndexOf('.'))
        if (fileExtension == ".bat" || fileExtension == ".cmd") {
          scripWrapper = "${SCRIPT_WRAPPER_DOS}"
        } 
        
        println "service dir is: ${serviceDirectory}; script is: ${script}"
        def fullPathScript = "${serviceDirectory}/${script}";
        new AntBuilder().sequential {
            echo(message: "${fullPathScript} will be mark as executable...")
            chmod(file: "${fullPathScript}", perm:"+xr")
            chmod(file: "${serviceDirectory}/${scripWrapper}", perm:"+xr")
        }

        // add the expected outputs to the map to pass them to the script wrapper
        def expectedOutputsList = "";
        if(expectedOutputs) {
            expectedOutputs.each { expectedOutputsList = expectedOutputsList.length() >  0 ? "${expectedOutputsList};$it" : "$it" }
            if(!argsMap) { argsMap = [:] }
            argsMap.put("EXPECTED_OUTPUTS", expectedOutputsList)
        }
        
        String[] environment = new EnvironmentBuilder().buildShOrBatchEnvironment(argsMap);

        println "Executing command ${serviceDirectory}/${scripWrapper} ${serviceDirectory}/${script}.\n environment is: ${environment}"
        def scriptProcess = "${serviceDirectory}/${scripWrapper} ${serviceDirectory}/${script}".execute(environment, null)
        def myOutputListener = new ProcessOutputListener()
        
        scriptProcess.consumeProcessOutputStream(myOutputListener)
        scriptProcess.consumeProcessErrorStream(System.out)

        scriptProcess.waitFor()
        def scriptExitValue = scriptProcess.exitValue()
        def processResult = myOutputListener.getResult(expectedOutputs)
        if (processResult != null && processResult.outputs != null && !processResult.outputs.isEmpty()) {
          def operationOutputs = context.attributes.thisInstance[CloudifyAttributesUtils.CLOUDIFY_OUTPUTS_ATTRIBUTE]?:[:];
          processResult.outputs.each { k, v -> operationOutputs.put("${operationFQN}:$k", v) }
          context.attributes.thisInstance[CloudifyAttributesUtils.CLOUDIFY_OUTPUTS_ATTRIBUTE] = operationOutputs
        }

        print """
      ----------${script} : bash : Return Code ----------
      Return Code : $scriptExitValue
      ---------------------------------\n
      """
        if(scriptExitValue) {
            throw new RuntimeException("Error executing the script ${script} (return code: $scriptExitValue)")
        } else {
            return processResult != null ? processResult.result : null
        }
    }

    /**
     * Execute a goovy script. Argument are passed to the groovy via the GroovyShell Binding, where they are injected as variables.
     * Consider to pass them as env vars
     * for a closure, we should not use the ServiceContextFactory as the context instance is already injected in the service file
     * Therefore, the caller script should have a defined "context" variable
     * */
    static def executeGroovy(context, groovyScript, Map argsMap, List expectedOutputs) {
        def operationFQN = argsMap?argsMap.remove(OPERATION_FQN):null;
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
            executionList.add(call{ executeGroovy(ServiceContextFactory.getServiceContext(), theScript, null, null) })
        }
        for(script in otherScripts) {
            def theScript = script
            executionList.add(call{ executeScript(theScript, null, null) })
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
            executionList.add(call{ executeGroovy(ServiceContextFactory.getServiceContext(), theScript, null, null) })
        }
        for(script in otherScripts) {
            def theScript = script
            executionList.add(call{ executeScript(theScript, null, null) })
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
    
    //
    static class ProcessOutputListener implements Appendable {
    
      private StringWriter outputBufffer = new StringWriter();
      
      // assume that the output names contains only word chars
      private Pattern outputDetectionRegex = ~/EXPECTED_OUTPUT_(\w+)=(.*)/
      
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
    
      ProcessOutputResult getResult(List expectedOutputs) {
        outputBufffer.flush()
        def outputString = outputBufffer.toString();
        if (outputString == null || outputString.size() == 0) {
        System.out.append("size:" + outputString.size())
          return null;
        }
        def outputs = [:]
        def lineList = outputString.readLines()
        if(expectedOutputs && !expectedOutputs.isEmpty()) {
            def lineIterator = lineList.iterator()
            while(lineIterator.hasNext()) {
                def line = lineIterator.next();
                def ouputMatcher = outputDetectionRegex.matcher(line)
                if (ouputMatcher.matches()) {
                    def detectedOuputName = ouputMatcher.group(1)
                    if (expectedOutputs.contains(detectedOuputName)) {
                        // add the output value in the map 
                        outputs.put(detectedOuputName, ouputMatcher.group(2));
                        // remove the iterator 
                        lineIterator.remove();
                    }
                } 
            }
        }
        // the outputs have been removed, so the last line is now the result of the exec
        def result = lineList.size() > 0 ? lineList[lineList.size() -1] : null
        return new ProcessOutputResult(result: result, outputs: outputs)     
      }    
    
    }

    // data structure for script result
    static class ProcessOutputResult {
      // the reult = the last line of the output
      String result
      // the expected output values
      Map outputs
    }
}