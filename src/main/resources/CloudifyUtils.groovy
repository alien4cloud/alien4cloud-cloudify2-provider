import groovy.transform.Synchronized

import java.util.concurrent.TimeUnit

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.cloudifysource.utilitydomain.context.ServiceContextFactory

public class CloudifyUtils {
  static  Logger log = getLogger(CloudifyUtils.class);
  static GigaSpacesEventsManager manager = new GigaSpacesEventsManager()

  @Synchronized
  static def putEvent(application, service, instanceId, eventResume) {
      manager.putEvent(application, service, instanceId, eventResume)
  }
  
  @Synchronized
  static def putBlockStorageEvent(application, service, instanceId, event, volumeId) {
    manager.putBlockStorageEvent(application, service, instanceId, event, volumeId)
  }
  
  @Synchronized
  static def putRelationshipOperationEvent(application, nodeResume, eventResume, source, target) {
      manager.putRelationshipOperationEvent(application, nodeResume, eventResume, source, target);
  }

  static def waitFor(cloudifyService, serviceToWait, List states) {
    def context = ServiceContextFactory.getServiceContext()

    log.info "Waiting for ${serviceToWait}"
    def endTime = java.lang.System.currentTimeMillis() + 3600L * 1000L
    def dbInstances = null
    while( java.lang.System.currentTimeMillis() < endTime && dbInstances == null) {
      def dbService = context.waitForService(cloudifyService, 1, TimeUnit.MINUTES)
      if(dbService!=null) {
        dbInstances = dbService.waitForInstances(dbService.numberOfPlannedInstances, 1, TimeUnit.MINUTES)
      }
    }
    log.info "Got service ${serviceToWait}"

    dbInstances.each() { instance ->
      manager.waitFor(context.getApplicationName(), serviceToWait, instance.getInstanceId(), states)
    }
  }
  
  static def getState(applicationName, nodeToCheck, instanceId) {
      return manager.getState(applicationName, nodeToCheck, instanceId)
  }
  
  static def toAbsolutePath(context, String relativePath) {
      return relativePath ? context.getServiceDirectory()+"/"+relativePath : null;
  }
  
  static def destroy() {
      manager.destroy()
  }
  
  /**
   * et the end time given a timeout. If timeoutInSec = null, then default one (600 seconds) is used 
   * 
   * @param timeoutInSec
   * @return
   */
  static def getTimeoutTime(def timeoutInSec) {
      def finalTimeout = timeoutInSec > 0 ? timeoutInSec : 600;
      return java.lang.System.currentTimeMillis() + finalTimeout * 1000
  }
  
  static def getLogger (Class clazz) {
      return getLogger(clazz.name);
  }
  
  static def getLogger (String name) {
      Logger logger = Logger.getLogger(name);
      logger.setLevel(Level.toLevel(DeploymentConstants.LOG_LEVEL, Level.INFO));
      return logger;
  }
}