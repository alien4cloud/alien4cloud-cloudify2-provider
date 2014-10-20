package alien4cloud.paas.cloudify2;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import alien4cloud.paas.cloudify2.events.AlienEvent;
import alien4cloud.paas.cloudify2.events.NodeInstanceState;
import alien4cloud.rest.utils.JsonUtil;

@Slf4j
public class CloudifyEventsListener {

    private static final String GET_EVENTS_END_POINT = "/events/getEvents";

    private static final String SERVICE_KEY = "service";

    private static final String APPLICATION_KEY = "application";

    private int currentEventIndex = 0;

    private DefaultHttpClient httpClient;
    private final URI endpoint;
    private final String application;
    private final String service;

    public CloudifyEventsListener(URI restEventEndpoint) throws URISyntaxException {
        this.endpoint = restEventEndpoint;
        this.application = null;
        this.service = null;
        this.httpClient = new DefaultHttpClient();
    }

    public CloudifyEventsListener(URI restEventEndpoint, String application, String service) throws URISyntaxException {
        this.endpoint = restEventEndpoint;
        this.application = application;
        this.service = service;
        this.httpClient = new DefaultHttpClient();
    }

    private String doGet(URIBuilder builder) throws URISyntaxException, IOException {
        URI uri = builder.build();
        log.debug("Query uri {}", uri);
        HttpGet request = new HttpGet(builder.build());
        HttpResponse httpResponse = httpClient.execute(request);
        return EntityUtils.toString(httpResponse.getEntity());
    }

    private String doDelete(URIBuilder builder) throws URISyntaxException, IOException {
        URI uri = builder.build();
        log.debug("Query uri {}", uri);
        HttpDelete request = new HttpDelete(builder.build());
        HttpResponse httpResponse = httpClient.execute(request);
        return EntityUtils.toString(httpResponse.getEntity());
    }

    /**
     * Validate that the connection can be established.
     *
     * @return "is running" string.
     * @throws ClientProtocolException
     * @throws URISyntaxException
     * @throws IOException
     */
    public String test() throws URISyntaxException, IOException {
        URIBuilder builder = new URIBuilder(endpoint.resolve("/events/test"));
        return doGet(builder);
    }

    public List<AlienEvent> getEvents() throws IOException, URISyntaxException {
        URIBuilder builder = new URIBuilder(endpoint.resolve(GET_EVENTS_END_POINT)).addParameter(APPLICATION_KEY, application)
                .addParameter(SERVICE_KEY, service).addParameter("lastIndex", "0");

        String response = doGet(builder);
        return JsonUtil.toList(response, AlienEvent.class);
    }

    public List<AlienEvent> getEventsSince(Date date, int maxEvents) throws URISyntaxException, IOException {
        long dateAsLong = date.getTime();

        URIBuilder builder = new URIBuilder(endpoint.resolve("/events/getEventsSince")).addParameter("dateAsLong", Long.toString(dateAsLong)).addParameter(
                "maxEvents", Integer.toString(maxEvents));

        String response = this.doGet(builder);
        return JsonUtil.toList(response, AlienEvent.class);
    }

    public List<NodeInstanceState> getNodeInstanceStates(String topologyId) throws URISyntaxException, IOException {
        URIBuilder builder = new URIBuilder(endpoint.resolve("/events/getInstanceStates")).addParameter(APPLICATION_KEY, topologyId);

        String response = this.doGet(builder);
        return JsonUtil.toList(response, NodeInstanceState.class);
    }

    public void deleteNodeInstanceStates(String topologyId) throws URISyntaxException, IOException {
        URIBuilder builder = new URIBuilder(endpoint.resolve("/events/deleteInstanceStates")).addParameter(APPLICATION_KEY, topologyId);
        this.doDelete(builder);
    }

    public List<AlienEvent> getNextEvents() throws URISyntaxException, IOException {
        URIBuilder builder = new URIBuilder(endpoint.resolve(GET_EVENTS_END_POINT)).addParameter(APPLICATION_KEY, application)
                .addParameter(SERVICE_KEY, service).addParameter("lastIndex", Integer.toString(this.currentEventIndex + 1));

        final String response = this.doGet(builder);

        List<AlienEvent> events = JsonUtil.toList(response, AlienEvent.class);
        if (events != null && !events.isEmpty()) {
            this.currentEventIndex = events.get(events.size() - 1).getEventIndex();
        }

        return events;
    }

    public AlienEvent getLatestEvent(String application, String service, String instanceId) throws URISyntaxException, IOException {
        URIBuilder builder = new URIBuilder(endpoint.resolve("/events/getLatestEvent")).addParameter(APPLICATION_KEY, application)
                .addParameter(SERVICE_KEY, service).addParameter("instanceId", instanceId);

        final String response = this.doGet(builder);
        if (StringUtils.isNotEmpty(response)) {
            AlienEvent events = JsonUtil.readObject(response, AlienEvent.class);
            return events;
        }
        return null;
    }
}
