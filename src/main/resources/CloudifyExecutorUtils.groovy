import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.*

import groovy.lang.Binding
import groovy.transform.Synchronized
import org.cloudifysource.utilitydomain.context.ServiceContextFactory
import org.apache.log4j.Logger;

public class CloudifyExecutorUtils {
    static Logger log = CloudifyUtils.getLogger(CloudifyExecutorUtils.class)

    static def counter = new AtomicInteger()
    static def threadPool = Executors.newFixedThreadPool(50, { r -> return new Thread(r as Runnable, "alien-executor-" + counter.incrementAndGet()) } as ThreadFactory )
    static def call = { c -> threadPool.submit(c as Callable) }

    static List startStates = [
        "initial",
        "created",
        "configured",
        "started",
        "available"
    ];
    static List shutdownStates = ["stopped", "deleted"];
    static def DEFAULT_LEASE = 1000 * 60 * 60
    static def OPERATION_FQN = "OPERATION_FQN";

    private static def SCRIPT_WRAPPER_NUX = "scriptWrapper.sh"
    private static def SCRIPT_WRAPPER_DOS = "scriptWrapper.bat"

    static def asyncExecutionList = []

    static synchronized addExecution(exec) {
        asyncExecutionList.add(exec);
    }

    /**
     * execute a script bash or batch
     * 
     * @param script
     * @param argsMap
     * @param expectedOutputs
     * @return
     */
    static def executeScript(context, script, Map argsMap, Map expectedOutputsToAttributes, def logLevel) {
        def operationFQN = argsMap?argsMap.remove(OPERATION_FQN):null;
        def serviceDirectory = context.getServiceDirectory()

        def scripWrapper = "${SCRIPT_WRAPPER_NUX}"
        def fileExtension = script.substring(script.lastIndexOf('.'))
        if (fileExtension == ".bat" || fileExtension == ".cmd") {
            scripWrapper = "${SCRIPT_WRAPPER_DOS}"
        }

        logOnLevel ("service dir is: ${serviceDirectory}; script is: ${script}", logLevel)
        def fullPathScript = "${serviceDirectory}/${script}";
        new AntBuilder().sequential {
            echo(message: "${fullPathScript} will be mark as executable...")
            chmod(file: "${fullPathScript}", perm:"+xr")
            chmod(file: "${serviceDirectory}/${scripWrapper}", perm:"+xr")
        }

        // add the expected outputs to the map to pass them to the script wrapper
        Set expectedOutputs = null;
        def expectedOutputsList = "";
        if(expectedOutputsToAttributes) {
            //outpts names
            expectedOutputs = expectedOutputsToAttributes.keySet();
            expectedOutputs.each { expectedOutputsList = expectedOutputsList.length() >  0 ? "${expectedOutputsList};$it" : "$it" }
            if(!argsMap) { argsMap = [:] }
            argsMap.put("EXPECTED_OUTPUTS", expectedOutputsList)
        }

        String[] environment = new EnvironmentBuilder().buildShOrBatchEnvironment(argsMap);

        logOnLevel("Executing command ${serviceDirectory}/${scripWrapper} ${serviceDirectory}/${script}.\n environment is: ${argsMap}", logLevel)
        def scriptProcess = "${serviceDirectory}/${scripWrapper} ${serviceDirectory}/${script}".execute(environment, null)
        def myOutputListener = new ProcessOutputListener()

        scriptProcess.consumeProcessOutputStream(myOutputListener)
        scriptProcess.consumeProcessErrorStream(System.out)

        scriptProcess.waitFor()
        def scriptExitValue = scriptProcess.exitValue()
        def processResult = myOutputListener.getResult(expectedOutputs)

        //process outputs
        if (processResult && processResult.outputs && !processResult.outputs.isEmpty()) {
            registerOutputsAndReferencingAttributes(context, operationFQN, processResult.outputs, expectedOutputsToAttributes);
        }
        
        if(scriptExitValue) {
            throw new RuntimeException("Error executing the script ${script} (return code: $scriptExitValue)")
        } else {
            logOnLevel( "sh result is: "+ ( processResult != null ? processResult.result : null ), logLevel)
            return processResult != null ? processResult.result : null
        }
    }

    /**
     * Execute a goovy script. Argument are passed to the groovy via the GroovyShell Binding, where they are injected as variables.
     * Consider to pass them as env vars
     * for a closure, we should not use the ServiceContextFactory as the context instance is already injected in the service file
     * Therefore, the caller script should have a defined "context" variable
     * */
    static def executeGroovy(context, groovyScript, Map argsMap, Map expectedOutputsToAttributes, def logLevel) {
        def operationFQN = argsMap?argsMap.remove(OPERATION_FQN):null;
        def serviceDirectory = context.getServiceDirectory()

        Binding binding = new EnvironmentBuilder().buildGroovyEnvironment(context, argsMap)

        //hack for a MissingPropertyException thrown for CloudifyExecutorUtils
        binding.setVariable("CloudifyExecutorUtils", this)
        def shell = new GroovyShell(CloudifyExecutorUtils.class.classLoader,binding)
        logOnLevel("Evaluating file ${serviceDirectory}/${groovyScript}.\n environment is: ${argsMap}", logLevel)
        def result = shell.evaluate(new File("${serviceDirectory}/${groovyScript}"))

        // now collect the value of outputs
        // child script should just affect them like : OUTPUT1 = "value1", or use the embebded method setProperty("OUTPUT1","value1")
        // but should not create local variables like : def OUTPUT1 = "value1"
        if(expectedOutputsToAttributes) {
            Map outputsWithValues = [:];
            Map bindingVars = binding.getVariables()?:[:];
            expectedOutputsToAttributes.keySet().each {
                outputsWithValues.put(it, bindingVars.get(it));
            }
            registerOutputsAndReferencingAttributes(context, operationFQN, outputsWithValues, expectedOutputsToAttributes);
        }
        logOnLevel( "result is: "+result, logLevel)
        return result
    }

    private static def registerOutputsAndReferencingAttributes(context, String operationFQN, Map outputsWithValues, Map outputsToAttributes) {
        def operationOutputs = context.attributes.thisInstance[CloudifyAttributesUtils.CLOUDIFY_OUTPUTS_ATTRIBUTE]?:[:];
        outputsWithValues.each { k, v ->
            //register outputs
            operationOutputs.put("${operationFQN}:$k", v)

            //register if needed referencing attributes
            def attributesToRegister = outputsToAttributes[k];
            if(attributesToRegister) {
                attributesToRegister.each {
                    context.attributes.thisInstance[it] = v;
                }
            }
        }
        context.attributes.thisInstance[CloudifyAttributesUtils.CLOUDIFY_OUTPUTS_ATTRIBUTE] = operationOutputs
    }

    static def executeParallel(groovyScripts, otherScripts) {
        def executionList = []
        if(groovyScripts) {
            for(script in groovyScripts) {
                log.info "parallel groovy launch is $script"
                def theScript = script
                executionList.add(call{ executeGroovy(ServiceContextFactory.getServiceContext(), theScript, null, null, "info") })
            }
        }
        
        if(otherScripts) {
            for(script in otherScripts) {
                log.info "parallel bash launch is $script"
                def theScript = script
                executionList.add(call{ executeScript(ServiceContextFactory.getServiceContext(), theScript, null, null, "info") })
            }
        }
        
        for(execution in executionList) {
            execution.get();
        }
    }

    static def executeAsync(groovyScripts, otherScripts) {
        List asynchExecs = [];
        if(groovyScripts) {
            for(script in groovyScripts) {
                log.info "asynchronous groovy launch is ${script}"
                def theScript = script;
                def futureExec = call{ executeGroovy(ServiceContextFactory.getServiceContext(), theScript, null, null, "info") };
                addExecution(futureExec);
                asynchExecs.add(futureExec);
            }
        }
        
        if(otherScripts) {
            for(script in otherScripts) {
                log.info "asynchronous bash launch is ${script}"
                def theScript = script;
                def futureExec = call{ executeScript(ServiceContextFactory.getServiceContext(), theScript, null, null, "info") };
                addExecution(futureExec);
                asynchExecs.add(futureExec);
            }
        }

        //return the list for checking purposes
        return asynchExecs;
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
        log.debug "THE INSTANCE ID is <${instanceId}>"
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
        log.debug "THE INSTANCE ID is <${instanceId}>"
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
        log.debug "THE INSTANCE ID is <${nodeResume.instanceId}>"
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
        log.debug "Got Last state for ${nodeToCheck}: ${state}";
        return validStates.contains(state);
    }

    static def shutdown() {
        log.info "Shutting down threadpool"
        for(execution in asyncExecutionList) {
            execution.get();
        }
        threadPool.shutdownNow();
        log.info "threadpool shut down!"
    }

    /**
     * Wait for some Future task completion.
     * Exit if a task completed with exception
     * 
     * @param futures
     * @param nodeId
     * @param timeoutInSecond
     * @return
     */
    static waitForFutures(def futures, def nodeId, def timeoutInSecond) {
        if(futures==null || futures.isEmpty()) {
            return;
        }
        def finalTimeout = timeoutInSecond > 0 ? timeoutInSecond :(600*9/10);
        log.info("Waiting for "+ futures.size() +" process to finish within a timeout of ${finalTimeout} seconds on node <${nodeId}>...");
        def maxTime = java.lang.System.currentTimeMillis() + (finalTimeout * 1000)
        boolean allDone = false;
        while(!allDone && java.lang.System.currentTimeMillis() < maxTime) {
            allDone = allFuturesDone(futures, nodeId);
            if(!allDone) {
                sleep 1000;
            }
        }
        
        if(!allDone) {
            //log.warning("Timeout of ${finalTimeout} seconds reached when waiting for ${futures.size()} asynch process to finish on node <${nodeId}>");
           throwException(new java.util.concurrent.TimeoutException("Timeout of ${finalTimeout} reached when waiting for ${futures.size()} asynch process to finish on node <${nodeId}>!"), nodeId);
        }
    }
    
    /**
     * return true if every future is done without error, false if not yet done
     * 
     * @return
     */
    static def allFuturesDone(def futures, String nodeId) {
        boolean done = true;
        for (Future future in futures) {
                try {
                    //if done, then check if no error or exceptions
                    if (future.isDone()) {
                        future.get();
                    }else {
                        done = false;
                    }
                } catch (Exception e) {
                 throwException(e, nodeId);   
                }
            }
        return done;
    }
    
    static throwException(Exception exception, def nodeId) {
        //update the node state to error
        fireEvent(nodeId, "error", null);
        
        //re throw the exception
        throw exception;
    }

    /**
     * get all the states after a specific one.
     * 
     * For ex, if the provided state is configured, then started and available are valids. 
     * 
     * @param state
     * @return
     */
    private static def getValidStates(String state) {
        if(startStates.contains(state) ) {
            int index =startStates.indexOf(state);
            return startStates.subList(index, startStates.size())
        }else if(shutdownStates.contains(state)) {
            int index = shutdownStates.indexOf(state)
            return shutdownStates.subList(index, shutdownStates.size())
        }
        return null;
    }

    private static def getEventLeaseInMillis(lease) {
        return lease ? Math.round(lease * 60 * 60 * 1000) : DEFAULT_LEASE;
    }
    
    static logOnLevel(def message, String logLevel) {
        def level = logLevel ?: "INFO";
        switch (level.toUpperCase()) {
            case "DEBUG":
                log.debug(message);
                break;
            case "ERROR":
                log.error(message);
                break;
            default:
                log.info(message);
                break;
        }
    }

}