import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import groovy.lang.Binding
import groovy.transform.Synchronized
import org.cloudifysource.dsl.context.ServiceInstance

import org.cloudifysource.utilitydomain.context.ServiceContextFactory

public class CloudifyAttributesUtils {

  static def getAttribute(context, cloudifyService, instanceId, attributeName) {
      
      println "CloudifyGetAttributesUtils.getAttribute: getting attribute <attr: ${attributeName}> < service: ${cloudifyService}> <instanceId: ${instanceId}>"
      def attr = null;
      if(attributeName != null ) {
          if(cloudifyService != null) {
              def serviceAttributes = context.attributes[cloudifyService];
              if( serviceAttributes != null) {
                  if(instanceId != null) {
                      def instanceAttributes = serviceAttributes.instances[instanceId];
                     attr = !instanceAttributes ?: instanceAttributes[attributeName];
                  //case instanceId is null: try get this instance, and if null, get the serviceLevel attribute
                  }else{
                      def instanceAttributes = serviceAttributes.instances[context.instanceId]
                      attr = (instanceAttributes && instanceAttributes[attributeName] ) ? instanceAttributes[attributeName] : serviceAttributes[attributeName];
                  }
              }
          //case cloudifyService is null: get the applicationLevel attribute
          }else {
              attr = context.attributes.thisApplication[attributeName] ;
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
          instanceId = context.instanceId <= service.numberOfPlannedInstances ? context.instanceId : 1
      }
      def requestedInstance = instances.find{ it.instanceId == instanceId } as ServiceInstance
      return ! requestedInstance ?: requestedInstance.getHostAddress() 
  }
  
  
}