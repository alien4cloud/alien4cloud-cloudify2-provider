import java.util.Date;

import org.openspaces.core.GigaSpace;
import org.openspaces.core.GigaSpaceConfigurer;
import org.openspaces.core.space.UrlSpaceConfigurer;

import com.gigaspaces.document.SpaceDocument;
import com.gigaspaces.metadata.SpaceTypeDescriptorBuilder;
import com.gigaspaces.query.QueryResultType;
import com.j_spaces.core.IJSpace;
import com.j_spaces.core.client.SQLQuery;

public class GigaSpacesEventsManager {

  def DEFAULT_LEASE = 1000 * 60 * 60;
  def DEFAULT_LOCATOR = "localhost:4176"
  def BLOCKSTORAGE_TYPE = "BLOCKSTORAGE"
  def INSTANCE_STATE_TYPE = "INSTANCE_STATE"
  GigaSpace gigaSpace
  UrlSpaceConfigurer spaceConfigurer

  def GigaSpacesEventsManager(String url = "jini://*/*/cloudifyManagementSpace", String locator = null) {
    println "[GigaSpacesEventsManager] Alien4Cloud event manager creating."
    if (locator == null) {
      locator = System.getenv('LUS_IP_ADDRESS') == null ? DEFAULT_LOCATOR : System.getenv('LUS_IP_ADDRESS')
    }
    printDebug("Connect to spaceUrl=${url}?locator=${locator}")
    
    this.spaceConfigurer = new UrlSpaceConfigurer(url).lookupLocators(locator); 
    IJSpace space = spaceConfigurer.space();
    this.gigaSpace = new GigaSpaceConfigurer(space).gigaSpace();
    println "[GigaSpacesEventsManager] Alien4Cloud event manager created."
  }

  def printDebug(message) {
    //if(System.getenv('DEBUG') != null) {
      println "[GigaSpacesEventsManager] ${message}"
    //}
  }
  
  def destroy() {
      println "[GigaSpacesEventsManager] Alien4Cloud event manager destroying."
      this.spaceConfigurer.destroy()
      println "[GigaSpacesEventsManager] Alien4Cloud event manager destroyed."
  }
  
  def putEvent(def application, def service, def instanceId, def event) {
      println ">>> putEvent application=${application} service=${service} instanceId=${instanceId} event=${event}"
      def type = INSTANCE_STATE_TYPE
      SpaceDocument document = new SpaceDocument("alien4cloud.paas.cloudify2.events.AlienEvent");
      fillEventDocument(application, service, instanceId, event, type, document)
      gigaSpace.write(document, DEFAULT_LEASE);
      putNodeInstanceStateEvent(application, service, instanceId, event)
  }
  
  def putBlockStorageEvent(def application, def service, def instanceId, def event, def volumeId) {
      println ">>> putBlockStorageEvent application=${application} service=${service} instanceId=${instanceId} event=${event} volumeId=${volumeId}"
      def type = BLOCKSTORAGE_TYPE
      SpaceDocument document = new SpaceDocument("alien4cloud.paas.cloudify2.events.BlockStorageEvent");
      fillEventDocument(application, service, instanceId, event, type, document);
      if(volumeId && volumeId != null) {
          document.setProperty("volumeId", volumeId as String);
      }
      gigaSpace.write(document, DEFAULT_LEASE)
      putNodeInstanceStateEvent(application, service, instanceId, event)
  }
  
  def putNodeInstanceStateEvent(def application, def service, def instanceId, def event) {
      def document = new SpaceDocument("alien4cloud.paas.cloudify2.events.NodeInstanceState");
      document.setProperty("id", application + "-" + service + "-" + instanceId);
      document.setProperty("topologyId", application as String);
      document.setProperty("nodeTemplateId", service as String);
      document.setProperty("instanceId", instanceId as String);
      document.setProperty("instanceState", event as String);
      gigaSpace.write(document);
  }
  
  def fillEventDocument(def application, def service, def instanceId, def event, def type, def document) {
      SQLQuery<SpaceDocument> template = new SQLQuery<SpaceDocument>(
          "alien4cloud.paas.cloudify2.events.AlienEvent"
          , String.format("applicationName='%s' and serviceName='%s' and instanceId='%s' ORDER BY eventIndex DESC", application, service, instanceId)
          , QueryResultType.DOCUMENT);

      SpaceDocument[] readMultiple = gigaSpace.readMultiple(template, 1);
      int lastIndex = 0;
      if (readMultiple != null && readMultiple.length > 0) {
          lastIndex = readMultiple[0].getProperty("eventIndex");
      }
      
      document.setProperty("applicationName", application as String);
      document.setProperty("serviceName", service as String);
      document.setProperty("instanceId", instanceId as String);
      document.setProperty("event", event as String);
      document.setProperty("type", type as String);
      document.setProperty("eventIndex", new Integer(lastIndex + 1));
      document.setProperty("dateTimestamp", new Date());
      
  }

  def waitFor(def applicationName, def serviceName, def instanceId, def event) {
    printDebug("Waiting for event=${event} from ${applicationName}.${serviceName}(${instanceId}) ...")
    boolean wait = true
    int lastIndex = 0

    while (wait) {
      String query = String.format("applicationName='%s' and serviceName='%s' and instanceId='%s' and eventIndex >= '%s' ORDER BY eventIndex"
        , applicationName, serviceName, instanceId, lastIndex + 1)

      SQLQuery<SpaceDocument> sqlQuery = new SQLQuery<SpaceDocument>("alien4cloud.paas.cloudify2.events.AlienEvent", query, QueryResultType.DOCUMENT)
      SpaceDocument[] readMultiple = gigaSpace.readMultiple(sqlQuery)

      if (readMultiple != null) {
        for (SpaceDocument spaceDocument : readMultiple) {
          String gotEvent = spaceDocument.getProperty("event").toString()
          if (event.equals(gotEvent)) {
            wait = false
            break
          }
          lastIndex = spaceDocument.getProperty("eventIndex")
        }

        if (wait) {
          printDebug("... still waiting 5 secondes for event=${event} from ${applicationName}.${serviceName}(${instanceId})")
          sleep 5000
        }
      }
    }
    printDebug("... ${applicationName}.${serviceName}(${instanceId}) reaches event=${event}")
  }
}
