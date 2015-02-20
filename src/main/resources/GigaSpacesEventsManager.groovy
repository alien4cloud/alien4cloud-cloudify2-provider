import java.util.Date;
import java.util.List;
import org.openspaces.core.GigaSpace;
import org.openspaces.core.GigaSpaceConfigurer;
import org.openspaces.core.space.UrlSpaceConfigurer;

import com.gigaspaces.document.SpaceDocument;
import com.gigaspaces.metadata.SpaceTypeDescriptorBuilder;
import com.gigaspaces.query.QueryResultType;
import com.j_spaces.core.IJSpace;
import com.j_spaces.core.client.SQLQuery;

public class GigaSpacesEventsManager {

    def DEFAULT_LOCATOR = "localhost:4176"
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
        println "[GigaSpacesEventsManager] Alien4Cloud event dispatching completed. Releasing resource...."
        this.spaceConfigurer.destroy()
        println "[GigaSpacesEventsManager] Alien4Cloud event manager resource released."
    }

    def putEvent(def application, def service, def instanceId, def eventResume) {
        println ">>> putEvent application=${application} service=${service} instanceId=${instanceId} event=${eventResume}"
        SpaceDocument document = new SpaceDocument("alien4cloud.paas.cloudify2.events.AlienEvent");
        fillEventDocument(application, service, instanceId, eventResume.event, document)
        gigaSpace.write(document, eventResume.lease);
        putNodeInstanceStateEvent(application, service, instanceId, eventResume.event)
    }

    def putBlockStorageEvent(def application, def service, def instanceId, def eventResume, def volumeId) {
        println ">>> putBlockStorageEvent application=${application} service=${service} instanceId=${instanceId} event=${eventResume} volumeId=${volumeId}"
        SpaceDocument document = new SpaceDocument("alien4cloud.paas.cloudify2.events.BlockStorageEvent");
        fillEventDocument(application, service, instanceId, eventResume.event, document);
        if(volumeId && volumeId != null) {
            document.setProperty("volumeId", volumeId as String);
        }
        gigaSpace.write(document, eventResume.lease)
        putNodeInstanceStateEvent(application, service, instanceId, eventResume.event)
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

    def fillEventDocument(def application, def service, def instanceId, def event, def document) {
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
        document.setProperty("eventIndex", new Integer(lastIndex + 1));
        document.setProperty("dateTimestamp", new Date());
    }

    def waitFor(def applicationName, def serviceName, def instanceId, List states) {
        printDebug("Waiting for state=${states[0]} from ${applicationName}.${serviceName}(${instanceId}) ...")
        boolean wait = true

        while (wait) {
            String query = String.format("topologyId='%s' and nodeTemplateId='%s' and instanceId='%s'"
                    , applicationName, serviceName, instanceId)

            SQLQuery<SpaceDocument> sqlQuery = new SQLQuery<SpaceDocument>("alien4cloud.paas.cloudify2.events.NodeInstanceState", query, QueryResultType.DOCUMENT)
            SpaceDocument[] readMultiple = gigaSpace.readMultiple(sqlQuery)

            if (readMultiple != null) {
                for (SpaceDocument spaceDocument : readMultiple) {
                    String gotState = spaceDocument.getProperty("instanceState").toString()
                    if (states.contains(gotState)) {
                        wait = false
                        break
                    }
                }

                if (wait) {
                    printDebug("... still waiting 5 secondes for state=${states[0]} from ${applicationName}.${serviceName}(${instanceId})")
                    sleep 5000
                }
            }
        }
        printDebug("... ${applicationName}.${serviceName}(${instanceId}) reaches state=${states[0]}")
    }

    def getState(def applicationName, def serviceName, def instanceId) {
        String query = String.format("topologyId='%s' and nodeTemplateId='%s' and instanceId='%s'"
                , applicationName, serviceName, instanceId)

        SQLQuery<SpaceDocument> sqlQuery = new SQLQuery<SpaceDocument>("alien4cloud.paas.cloudify2.events.NodeInstanceState", query, QueryResultType.DOCUMENT)
        SpaceDocument[] readMultiple = gigaSpace.readMultiple(sqlQuery)
        def gotState = null
        if (readMultiple != null && readMultiple.length > 0) {
            SpaceDocument spaceDocument = readMultiple[0]
            gotState = spaceDocument.getProperty("instanceState").toString()
        }
        return gotState
    }
    
    def putRelationshipOperationEvent(def application, def service, def instanceId, def eventResume, def source, def target) {
        println ">>> putRelationshipOperationEvent application=${application} service=${service} instanceId=${instanceId} event=${eventResume}\n"+
                "source=${source} target=${target}"
        SpaceDocument document = new SpaceDocument("alien4cloud.paas.cloudify2.events.RelationshipOperationEvent");
        fillEventDocument(application, service, instanceId, eventResume.event, document);
        
        document.setProperty("executed", false as Boolean);
        document.setProperty("relationshipId", eventResume.relationshipId as String);
        document.setProperty("source", source.name as String);
        document.setProperty("sourceService", source.service as String);
        document.setProperty("target", target.name as String);
        document.setProperty("targetService", target.service as String);
        document.setProperty("commandName", eventResume.commandName as String);
        
        gigaSpace.write(document, eventResume.lease)
    }
}
