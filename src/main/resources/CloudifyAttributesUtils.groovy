import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import groovy.lang.Binding
import groovy.transform.Synchronized
import org.cloudifysource.dsl.context.ServiceInstance

import org.cloudifysource.utilitydomain.context.ServiceContextFactory

public class CloudifyAttributesUtils {

  static def getAttribute(cloudifyService, instanceId, attributeName) {
      def context = ServiceContextFactory.getServiceContext()
      
      println "CloudifyGetAttributesUtils.getAttribute: getting attribute <attr: ${attributeName}> < service: ${cloudifyService}> <instanceId: ${instanceId}>"
      def attr = null;
      if(attributeName != null ) {
          if(cloudifyService != null) {
              def serviceAttributes = context.attributes[cloudifyService];
              if( serviceAttributes != null) {
                  if(instanceId != null) {
                      def instanceAttributes = serviceAttributes.instances[instanceId];
                      attr == null ?: instanceAttributes[attributeName];
                  //case instanceId is null: get the serviceLevel attribute
                  }else {
                      attr = serviceAttributes[attributeName];
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
  
  static def getIp(String cloudifyService, instanceId) {
      def context = ServiceContextFactory.getServiceContext()
      println "CloudifyGetAttributesUtils.getIp: retrieving the Ip address < service: ${cloudifyService}> <instanceId: ${instanceId}>"
      if(cloudifyService == null) {
          println "CloudifyGetAttributesUtils.getIp: no service name provided. Will return the current instance private Ip"
          return context.getPrivateAddress();
      }
      
      def service = context.waitForService(cloudifyService, 60, TimeUnit.MINUTES)
      def instances = service.waitForInstances(service.numberOfPlannedInstances, 60, TimeUnit.MINUTES)
      if(instanceId == null) {
          println "CloudifyGetAttributesUtils.getIp: no instanceId provided. Will use the current instanceId, or will take the first instance."
          instanceId = context.instanceId <= service.numberOfPlannedInstances ? : 1
      }
      def requestedInstance = instances.find{ it.instanceId == instanceId } as ServiceInstance
      return requestedInstance == null ? : requestedInstance.getHostAddress() 
  }
  
  
}