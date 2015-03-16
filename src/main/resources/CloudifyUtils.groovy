import groovy.transform.Synchronized
import java.util.concurrent.TimeUnit
import org.cloudifysource.utilitydomain.context.ServiceContextFactory

public class CloudifyUtils {
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
    def dbService = context.waitForService(cloudifyService, 60, TimeUnit.MINUTES)
    def dbInstances = dbService.waitForInstances(dbService.numberOfPlannedInstances, 60, TimeUnit.MINUTES)

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
}