package alien4cloud.paas.cloudify2.rest;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.utils.URIBuilder;

import alien4cloud.paas.cloudify2.events.AlienEvent;
import alien4cloud.paas.cloudify2.events.NodeInstanceState;
import alien4cloud.rest.utils.JsonUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

@Slf4j
public class CloudifyEventsListener {

    private static final String GET_EVENTS_END_POINT = "/events/getEvents";

    private static final String SERVICE_KEY = "service";

    private static final String DEPLOYMENT_ID_KEY = "deploymentId";

    private RestExecutor restExecutor;
    private final URI endpoint;
    private final String deploymentId;
    private final String service;

    public CloudifyEventsListener(URI restEventEndpoint) throws URISyntaxException {
        this(restEventEndpoint, null, null);
    }

    public CloudifyEventsListener(URI restEventEndpoint, String deploymentId, String service) throws URISyntaxException {
        this.endpoint = restEventEndpoint;
        this.deploymentId = deploymentId;
        this.service = service;
        this.restExecutor = new RestExecutor();
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
        return restExecutor.doGet(builder, true);
    }

    public List<AlienEvent> getEvents() throws IOException, URISyntaxException {
        URIBuilder builder = new URIBuilder(endpoint.resolve(GET_EVENTS_END_POINT)).addParameter(DEPLOYMENT_ID_KEY, deploymentId)
                .addParameter(SERVICE_KEY, service).addParameter("lastIndex", "0");

        String response = restExecutor.doGet(builder, true);
        return new ObjectMapper().readValue(response, new TypeReference<List<AlienEvent>>() {
        });
        // return JsonUtil.toList(response, AlienEvent.class);
    }

    public List<AlienEvent> getEventsSince(Date date, int maxEvents) throws URISyntaxException, IOException {
        long dateAsLong = date.getTime();

        URIBuilder builder = new URIBuilder(endpoint.resolve("/events/getEventsSince")).addParameter("dateAsLong", Long.toString(dateAsLong)).addParameter(
                "maxEvents", Integer.toString(maxEvents));

        String response = restExecutor.doGet(builder, false);

        if (StringUtils.isNotBlank(response)) {
            return new ObjectMapper().readValue(response, new TypeReference<List<AlienEvent>>() {
            });
        } else {
            return Lists.<AlienEvent> newArrayList();
        }
    }

    public List<NodeInstanceState> getNodeInstanceStates(String deploymentId) throws URISyntaxException, IOException {
        URIBuilder builder = new URIBuilder(endpoint.resolve("/events/getInstanceStates")).addParameter(DEPLOYMENT_ID_KEY, deploymentId);

        String response = restExecutor.doGet(builder, false);
        return StringUtils.isNotBlank(response) ? JsonUtil.toList(response, NodeInstanceState.class) : Lists.<NodeInstanceState> newArrayList();
    }

    public void deleteNodeInstanceStates(String deploymentId) throws URISyntaxException, IOException {
        URIBuilder builder = new URIBuilder(endpoint.resolve("/events/deleteInstanceStates")).addParameter(DEPLOYMENT_ID_KEY, deploymentId);
        restExecutor.doDelete(builder);
    }

    public void putNodeInstanceStates(List<NodeInstanceState> nodeInstanceStates) throws URISyntaxException, IOException {
        URIBuilder builder = new URIBuilder(endpoint.resolve("/events/putInstanceStates"));
        restExecutor.doPost(builder, nodeInstanceStates, true);
    }

}
