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
  GigaSpace gigaSpace
  UrlSpaceConfigurer spaceConfigurer

  def GigaSpacesEventsManager(String url = "jini://*/*/cloudifyManagementSpace", String locator = null) {
    if (locator == null) {
      locator = System.getenv('LUS_IP_ADDRESS') == null ? DEFAULT_LOCATOR : System.getenv('LUS_IP_ADDRESS')
    }
    printDebug("Connect to spaceUrl=${url}?locator=${locator}")
    
    this.spaceConfigurer = new UrlSpaceConfigurer(url).lookupLocators(locator); 
    IJSpace space = spaceConfigurer.space();
    this.gigaSpace = new GigaSpaceConfigurer(space).gigaSpace();
  }

  def printDebug(message) {
    //if(System.getenv('DEBUG') != null) {
      println "[GigaSpacesEventsManager] ${message}"
    //}
  }
  
  def destroy() {
      this.spaceConfigurer.destroy()
  }

  def putEvent(def application, def service, def instanceId, def event) {
      println ">>> putEvent application=${application} service=${service} instanceId=${instanceId} event=${event}"
      SQLQuery<SpaceDocument> template = new SQLQuery<SpaceDocument>(
          "fr.fastconnect.events.rest.CloudifyEvent"
          , String.format("applicationName='%s' and serviceName='%s' and instanceId='%s' ORDER BY eventIndex DESC", application, service, instanceId)
          , QueryResultType.DOCUMENT);

      SpaceDocument[] readMultiple = gigaSpace.readMultiple(template, 1);
      int lastIndex = 0;
      if (readMultiple != null && readMultiple.length > 0) {
          lastIndex = readMultiple[0].getProperty("eventIndex");
      }

      SpaceDocument document = new SpaceDocument("fr.fastconnect.events.rest.CloudifyEvent");
      document.setProperty("applicationName", application as String);
      document.setProperty("serviceName", service as String);
      document.setProperty("instanceId", instanceId as String);
      document.setProperty("event", event as String);
      document.setProperty("eventIndex", new Integer(lastIndex + 1));
      document.setProperty("dateTimestamp", new Date());
      gigaSpace.write(document, DEFAULT_LEASE);
      
      document = new SpaceDocument("fr.fastconnect.events.rest.NodeInstanceState");
      document.setProperty("id", application + "-" + service + "-" + instanceId);
      document.setProperty("topologyId", application as String);
      document.setProperty("nodeTemplateId", service as String);
      document.setProperty("instanceId", instanceId as String);
      document.setProperty("instanceState", event as String);
      gigaSpace.write(document);
  }

  def waitFor(def applicationName, def serviceName, def instanceId, def event) {
    printDebug("Waiting for event=${event} from ${applicationName}.${serviceName}(${instanceId}) ...")
    boolean wait = true
    int lastIndex = 0

    while (wait) {
      String query = String.format("applicationName='%s' and serviceName='%s' and instanceId='%s' and eventIndex >= '%s' ORDER BY eventIndex"
        , applicationName, serviceName, instanceId, lastIndex + 1)

      SQLQuery<SpaceDocument> sqlQuery = new SQLQuery<SpaceDocument>("fr.fastconnect.events.rest.CloudifyEvent", query, QueryResultType.DOCUMENT)
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
