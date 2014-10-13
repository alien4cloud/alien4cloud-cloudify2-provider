import groovy.transform.Synchronized
import java.util.concurrent.TimeUnit
import org.cloudifysource.utilitydomain.context.ServiceContextFactory

public class CloudifyUtils {
  static def manager = new GigaSpacesEventsManager()

  @Synchronized
  static def putEvent(application, service, instanceId, event) {
    manager.putEvent(application, service, instanceId, event)
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
  
  static def destroy() {
      manager.destroy()
  }
}