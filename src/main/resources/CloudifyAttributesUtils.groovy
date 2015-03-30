import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import groovy.lang.Binding
import groovy.transform.Synchronized
import org.cloudifysource.dsl.context.ServiceInstance

public class CloudifyAttributesUtils {

    public static IP_ADDR = "ip_address"
    public static def ATTRIBUTES_KEYWORD = "ATTR_"
    public static def ATTR_SEPARATOR = ","

    static def getAttribute(context, cloudifyService, instanceId, attributeName) {
        def parsedInstanceId = instanceId? instanceId as int : null
        println "CloudifyGetAttributesUtils.getAttribute: getting attribute <attr: ${attributeName}> < service: ${cloudifyService}> <instanceId: ${instanceId}>"
        def attr = null;
        if(attributeName != null && cloudifyService != null) {
            def serviceAttributes = context.attributes[cloudifyService];
            if( serviceAttributes != null) {
                 def instanceAttributes = serviceAttributes.instances[parsedInstanceId?:context.instanceId];
                 attr = !instanceAttributes ? null : instanceAttributes[attributeName];
            }
        }
        println "CloudifyGetAttributesUtils.getAttribute: Got [${attr}]"
        return attr;
    }

    /**
     * Get the Ip of an instance
     */
    static def getIp(context, String cloudifyService, instanceId) {
        def parsedInstanceId = instanceId? instanceId as int : null
        println "CloudifyGetAttributesUtils.getIp: retrieving the Ip address < service: ${cloudifyService}> <instanceId: ${parsedInstanceId}>"
        if(!cloudifyService // no service name provided, or
            ||(cloudifyService == context.serviceName //current service name provided, and
                && (instanceId == null || instanceId == context.instanceId) //current or null instance id provided
               )) {
            println "CloudifyGetAttributesUtils.getIp: Returning the current instance private Ip: ${context.getPrivateAddress()}"
            return context.getPrivateAddress();
        }

        def requestedInstance = null
        def ip = null
        def service = context.waitForService(cloudifyService, 10, TimeUnit.MINUTES)
        def instances = service.waitForInstances(service.numberOfPlannedInstances, 10, TimeUnit.MINUTES)
        requestedInstance = instances.find{ it.instanceId == parsedInstanceId } as ServiceInstance
        
        if(!requestedInstance) {
            println "CloudifyGetAttributesUtils.getIp: Cannot find an instance of < service: ${cloudifyService}> with the id <${parsedInstanceId}>. will take the first instance of service <${cloudifyService}>> found "
            requestedInstance = service.waitForInstances(1, 10, TimeUnit.MINUTES)[0] as ServiceInstance
        }
        ip = requestedInstance ? requestedInstance.getHostAddress() : null
        println "CloudifyGetAttributesUtils.getIp: Got for ${cloudifyService}[${parsedInstanceId}]: ${ip}"
        return ip
    }


    static def getTheProperAttribute(context, cloudifyService, instanceId, attributeName) {
        if(attributeName == IP_ADDR) {
            return getIp(context, cloudifyService, instanceId)
        }else {
            return getAttribute(context, cloudifyService, instanceId, attributeName)
        }
    }
    
    public static Map processAttributesEnvVar(context, Map argsMap) {
        Map attributesToProcess = [:]
        argsMap.each { k, v ->
            if(k.startsWith(ATTRIBUTES_KEYWORD)) {
                attributesToProcess.put(k.substring(ATTRIBUTES_KEYWORD.size()), v)
            }
        }
        attributesToProcess.each { k, v ->
            println "processing: ${k} -- ${v}"
            def index = v.indexOf(ATTR_SEPARATOR)
            if(index >= 0) {
                def serviceName = v.substring(0, index)
                def attrName = v.substring(index + 1)
                argsMap.put(k, getTheProperAttribute(context, serviceName, context.instanceId, attrName))
                println "processed: ${k} -- ${v}"
            }
            //remove them from the main argsMap
            println argsMap.remove(ATTRIBUTES_KEYWORD+k);
        }
        
        return attributesToProcess;
    }
}