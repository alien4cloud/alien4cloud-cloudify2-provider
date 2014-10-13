@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.5.0-RC2' )
import groovyx.net.http.*
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*

public class EventsRestClient {

  def http

  EventsManager(def endpoint) {
    this.http = new HTTPBuilder( endpoint )
  }

  def getAllEvents() {
    return doRequest('/events/getAllEvents')
  }

  def getEvents(def applicationName=null, def serviceName=null, def instanceId=null, def lastIndex=null) {
    return doRequest('/events/getEvents', ['application': applicationName, 'service': serviceName, 'instanceId': instanceId, 'lastIndex': lastIndex])
  }

  def putEvent(def applicationName, def serviceName, def instanceId, def lifecycleEvent) {
    return doRequest('/events/putEvent', ['application': applicationName, 'service': serviceName, 'instanceId': instanceId, 'event': lifecycleEvent])
  }

  def doRequest(def path, def params=null) {

    println "request ${http.uri}${path} with parameters=${params}"

    http.request( GET, JSON ) {
      uri.path = path
      if(params != null) {
        uri.query = params
      }

      // response handler for a success response code:
      response.success = { resp, json ->
        return json
      }

      // handler for any failure status code:
      response.failure = { resp ->
        println "Unexpected error: ${resp.statusLine.statusCode} : ${resp.statusLine.reasonPhrase}"
        System.exit(1)
      }
    }
  }

  def waitFor(def application, def serviceName, def instanceId, def event) {
    println "Waiting for event=${event} from ${application}.${serviceName}(${instanceId}) ..."
    def lastIndex = 0
    def wait = true
    while(wait) {
      def events = this.getEvents(application, serviceName, instanceId, lastIndex+1)
      def gotEvent = events.find() { it -> event.equals(it.event)}
      if(gotEvent == null) {
        println "... still waiting 5 secondes for event=${event} from ${application}.${serviceName}(${instanceId})"
        sleep 5000
      } else {
        wait = false
      }
    }
    println "... ${application}.${serviceName}(${instanceId}) reaches event=${event}"
  }
}