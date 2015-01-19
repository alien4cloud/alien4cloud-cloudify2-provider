import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import groovy.lang.Binding
import groovy.transform.Synchronized
import org.cloudifysource.dsl.context.ServiceInstance

import org.cloudifysource.utilitydomain.context.ServiceContextFactory

public class CloudifyAttributesUtils {

    public static IP_ADDR = "ip_address"
    public static def ATTRIBUTES_KEYWORD = "ATTR_"
    public static def ATTR_SEPARATOR = ","

    static def getAttribute(context, cloudifyService, instanceId, attributeName) {
        println "CloudifyGetAttributesUtils.getAttribute: getting attribute <attr: ${attributeName}> < service: ${cloudifyService}> <instanceId: ${instanceId}>"
        def attr = null;
        if(attributeName != null && cloudifyService != null) {
            def serviceAttributes = context.attributes[cloudifyService];
            if( serviceAttributes != null) {
                 def instanceAttributes = serviceAttributes.instances[instanceId?:context.instanceId];
                 attr = !instanceAttributes ?: instanceAttributes[attributeName];
            }
        }
        println "CloudifyGetAttributesUtils.getAttribute: Got [${attr}]"
        return attr;
    }

    /**
     * Get the Ip of an instance
     */
    static def getIp(context, String cloudifyService, instanceId) {
        println "CloudifyGetAttributesUtils.getIp: retrieving the Ip address < service: ${cloudifyService}> <instanceId: ${instanceId}>"
        if(! cloudifyService ) {
            println "CloudifyGetAttributesUtils.getIp: no service name provided. Will return the current instance private Ip"
            return context.getPrivateAddress();
        }

        def service = context.waitForService(cloudifyService, 60, TimeUnit.MINUTES)
        def instances = service.waitForInstances(service.numberOfPlannedInstances, 60, TimeUnit.MINUTES)
        if(! instanceId ) {
            println "CloudifyGetAttributesUtils.getIp: no instanceId provided. Will use the current instanceId, or will take the first instance."
            instanceId = context.instanceId
        }
        def requestedInstance = instances.find{ it.instanceId == instanceId } as ServiceInstance
        def ip = ! requestedInstance ?: requestedInstance.getHostAddress()
        println "CloudifyGetAttributesUtils.getIp: got [${ip}]"
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