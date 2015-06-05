import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

import groovy.lang.Binding

import org.cloudifysource.dsl.context.ServiceInstance

public class CloudifyAttributesUtils {

    public static IP_ADDR = "ip_address";
    private static COLON_SEPARATOR = ":";
    public static def CLOUDIFY_OUTPUTS_ATTRIBUTE = "OPERATIONS_OUTPUTS";
    private static def DEFAULT_TRIAL_COUNT = 5;

    /**
     * get a specific attribute
     * @param context
     * @param cloudifyService
     * @param instanceId
     * @param attributeName
     * @return
     */
    static def getAttribute(context, cloudifyService, instanceId, attributeName, List eligibleNodesNames) {
        println "CloudifyAttributesUtils.getAttribute: getting attribute <attr: ${attributeName}> < service: ${cloudifyService}> <instanceId: ${instanceId}> from nodes <${eligibleNodesNames}>"
        def attr = null;
        def countLeft = DEFAULT_TRIAL_COUNT;
        def check = true;
        if(attributeName) {
            //try it for 5 seconds
            while(check) {
                if(!eligibleNodesNames || eligibleNodesNames.isEmpty()) {
                    attr = getAttributeFromContext(context, cloudifyService, instanceId, attributeName);
                }else {
                    List nodeNames = eligibleNodesNames.collect()
                    while(!attr && !nodeNames.isEmpty()) {
                        def keyToFetch = nodeNames.get(0)+COLON_SEPARATOR+attributeName;
                        attr = getAttributeFromContext(context, cloudifyService, instanceId, keyToFetch);
                        nodeNames.remove(0);
                    }
                }
                countLeft--;
                check = wait(attr, countLeft);
            }
        }
        println "CloudifyAttributesUtils.getAttribute: Got [${attr}]";
        return attr;
    }
    
    /**
     * get a specific operation output
     * 
     * Try for 5 seconds if not found at first trial
     * @param context
     * @param cloudifyService
     * @param instanceId
     * @param formatedOutputName
     * @param eligibleNodesNames
     *          List of eligible nodes names for which to check the output
     * @return
     */
    static def getOperationOutput(context, cloudifyService, instanceId, formatedOutputName, List eligibleNodesNames) {
        println "CloudifyAttributesUtils.getOperationOutput: getting operation output <formatedOutputName: ${formatedOutputName}> < service: ${cloudifyService}> <instanceId: ${instanceId}> from nodes <${eligibleNodesNames}>";
        def outputValue = null;
        def countLeft = DEFAULT_TRIAL_COUNT;
        def check = true;
        
        if(formatedOutputName) {
            //try it for 5 seconds
            while(check) {
                def outputsMap = getAttributeFromContext(context, cloudifyService, instanceId, CLOUDIFY_OUTPUTS_ATTRIBUTE) as Map;
                if(outputsMap) {
                    if(!eligibleNodesNames || eligibleNodesNames.isEmpty()) {
                        outputValue = outputsMap[formatedOutputName];
                    }else {
                        List nodeNames = eligibleNodesNames.collect() 
                        while(!outputValue && !nodeNames.isEmpty()) {
                            def keyToFetch = nodeNames.get(0)+COLON_SEPARATOR+formatedOutputName;
                            def foundEntry = outputsMap.find {it.key == keyToFetch}
                            outputValue = foundEntry?foundEntry.value:null;
                            nodeNames.remove(0);
                        }
                    }
                }
                countLeft--;
                check = wait(outputValue, countLeft);
            }
        }
        println "CloudifyAttributesUtils.getOperationOutput: Got [${outputValue}]";
        return outputValue;
    }
    
    private static def wait(valueToCheck, counter) {
        if(!valueToCheck && counter>0) {
            sleep 1000
            return true
        }
        return false;
    }
    
    /**
     * Get the Ip of an instance
     */
    static def getIp(context, String cloudifyService, instanceId) {
        def parsedInstanceId = instanceId? instanceId as int : null
        println "CloudifyAttributesUtils.getIp: retrieving the Ip address < service: ${cloudifyService}> <instanceId: ${parsedInstanceId}>"
        if(!cloudifyService // no service name provided, or
            ||(cloudifyService == context.serviceName //current service name provided, and
                && (instanceId == null || instanceId == context.instanceId) //current or null instance id provided
               )) {
            println "CloudifyAttributesUtils.getIp: Returning the current instance private Ip: ${context.getPrivateAddress()}"
            return context.getPrivateAddress();
        }

        def requestedInstance = null
        def ip = null
        def service = context.waitForService(cloudifyService, 10, TimeUnit.MINUTES)
        def instances = service.waitForInstances(service.numberOfPlannedInstances, 10, TimeUnit.MINUTES)
        requestedInstance = instances.find{ it.instanceId == parsedInstanceId } as ServiceInstance
        
        if(!requestedInstance) {
            println "CloudifyAttributesUtils.getIp: Cannot find an instance of < service: ${cloudifyService}> with the id <${parsedInstanceId}>. will take the first instance of service <${cloudifyService}>> found "
            requestedInstance = service.waitForInstances(1, 10, TimeUnit.MINUTES)[0] as ServiceInstance
        }
        ip = requestedInstance ? requestedInstance.getHostAddress() : null
        println "CloudifyAttributesUtils.getIp: Got for ${cloudifyService}[${parsedInstanceId}]: ${ip}"
        return ip
    }

    static def getTheProperAttribute(context, cloudifyService, instanceId, attributeName, List eligibleNodesNames) {
        if(attributeName == IP_ADDR) {
            return getIp(context, cloudifyService, instanceId)
        }else {
            return getAttribute(context, cloudifyService, instanceId, attributeName, eligibleNodesNames)
        }
    }
    
    private static def getAttributeFromContext(context, cloudifyService, instanceId, attributeName) {
        def parsedInstanceId = instanceId? instanceId as int : null;
        def attr = null;
        cloudifyService = cloudifyService?:context.serviceName;
        if(attributeName != null) {
            def serviceAttributes = context.attributes[cloudifyService];
            if( serviceAttributes != null) {
                 def instanceAttributes = serviceAttributes.instances[parsedInstanceId?:context.instanceId];
                 attr = !instanceAttributes ? null : instanceAttributes[attributeName];
            }
        }
        return attr;
    }
   
}