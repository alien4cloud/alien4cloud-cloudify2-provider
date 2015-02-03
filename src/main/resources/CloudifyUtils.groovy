import groovy.transform.Synchronized
import java.util.concurrent.TimeUnit
import org.cloudifysource.utilitydomain.context.ServiceContextFactory

public class CloudifyUtils {
  static def manager = new GigaSpacesEventsManager()

  @Synchronized
  static def putEvent(application, service, instanceId, event) {
      manager.putEvent(application, service, instanceId, event)
  }
  
  @Synchronized
  static def putBlockStorageEvent(application, service, instanceId, event, volumeId) {
    manager.putBlockStorageEvent(application, service, instanceId, event, volumeId)
  }

  static def waitFor(cloudifyService, serviceToWait, event) {
    def context = ServiceContextFactory.getServiceContext()
    // FIXME Added the "Compute" suffix to the service name to match the serviceName in Cloudify.
    // This is a hard coded fix that must be handled differently
    def dbService = context.waitForService(cloudifyService, 60, TimeUnit.MINUTES)
    def dbInstances = dbService.waitForInstances(dbService.numberOfPlannedInstances, 60, TimeUnit.MINUTES)

    dbInstances.each() { instance ->
      manager.waitFor(context.getApplicationName(), serviceToWait, instance.getInstanceId(), event)
    }
  }
  
  static def getLastEvent(applicationName, cloudifyService, nodeToCheck, instanceId) {
      return manager.getLastEvent(applicationName, nodeToCheck, instanceId)
  }
  
  static def toAbsolutePath(context, String relativePath) {
      return relativePath ? context.getServiceDirectory()+"/"+relativePath : null;
  }
  
  static def destroy() {
      manager.destroy()
  }
}