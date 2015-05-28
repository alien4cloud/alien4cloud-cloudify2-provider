import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import groovy.lang.Binding
import groovy.transform.Synchronized
import org.cloudifysource.dsl.context.ServiceInstance

public class CloudifyAttributesUtils {

    public static IP_ADDR = "ip_address";
    private static OUTPUT_SEPARATOR = ":";
    public static def ATTR_SEPARATOR = ",";
    public static def CLOUDIFY_OUTPUTS_ATTRIBUTE = "OPERATIONS_OUTPUTS";

    /**
     * get a specific attribute
     * @param context
     * @param cloudifyService
     * @param instanceId
     * @param attributeName
     * @return
     */
    static def getAttribute(context, cloudifyService, instanceId, attributeName) {
        println "CloudifyAttributesUtils.getAttribute: getting attribute <attr: ${attributeName}> < service: ${cloudifyService}> <instanceId: ${instanceId}>"
        def attr = getAttributeFromContext(context, cloudifyService, instanceId, attributeName);
        println "CloudifyAttributesUtils.getAttribute: Got [${attr}]";
        return attr;
    }
    
    /**
     * get a specific operation output
     * @param context
     * @param cloudifyService
     * @param instanceId
     * @param formatedOutputName
     * @param eligibleNodesNames
     *          List of eligible nodes names for which to check the output
     * @return
     */
    static def getOperationOutput(context, cloudifyService, instanceId, formatedOutputName, List eligibleNodesNames) {
        println "CloudifyAttributesUtils.getOperationOutput: getting operation output <formatedOutputName: ${formatedOutputName}> < service: ${cloudifyService}> <instanceId: ${instanceId}>";
        def outputValue = null;
        if(formatedOutputName) {
            def outputsMap = getAttributeFromContext(context, cloudifyService, instanceId, CLOUDIFY_OUTPUTS_ATTRIBUTE);
            if(outputsMap) {
                if(!eligibleNodesNames || eligibleNodesNames.isEmpty()) {
                    outputValue = outputsMap[formatedOutputName];
                }else {
                    while(!outputValue && !eligibleNodesNames.isEmpty()) {
                        def keyToFetch = eligibleNodesNames.get(0)+OUTPUT_SEPARATOR+formatedOutputName;
                        outputValue = outputsMap[keyToFetch];
                        eligibleNodesNames.remove(0);
                    }
                }
            }
        }
        println "CloudifyAttributesUtils.getOperationOutput: Got [${outputValue}]";
        return outputValue;
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

    static def getTheProperAttribute(context, cloudifyService, instanceId, attributeName) {
        if(attributeName == IP_ADDR) {
            return getIp(context, cloudifyService, instanceId)
        }else {
            return getAttribute(context, cloudifyService, instanceId, attributeName)
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