import org.cloudifysource.utilitydomain.context.ServiceContextFactory
import EventsRestClient

public class CloudifyEventsManager {

  static def eventsRestClient = new EventsRestClient('http://${managementIp}:${restEventsPort}')

  static {
    env = System.getenv()
    locators = env['LUS_IP_ADDRESS'] == null ? "localhost:4174" : env['LUS_IP_ADDRESS']
    managementIp = locators.split(":")[0]
    restEventsPort = 8081
    eventsRestClient = new EventsRestClient('http://${managementIp}:${restEventsPort}')
  }

  static def getAllEvents() {
    eventsRestClient.getAllEvents()
  }

  static def waitFor(applicationToWait, serviceToWait, event) {
    def context = ServiceContextFactory.getServiceContext()
    def dbService = context.waitForService(serviceToWait, 20, TimeUnit.SECONDS)
    def dbInstances = dbService.waitForInstances(dbService.numberOfPlannedInstances, 60, TimeUnit.SECONDS)

    def manager = new EventsManager("http://${managementIp}:${restEventsPort}")

    dbInstances.each() { instance ->
      manager.waitFor(applicationToWait, serviceToWait, instance.getInstanceId(), event)
    }
  }

}
