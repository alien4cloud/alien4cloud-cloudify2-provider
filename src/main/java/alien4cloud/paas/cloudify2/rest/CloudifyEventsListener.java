package alien4cloud.paas.cloudify2.rest;

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
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import alien4cloud.paas.cloudify2.events.AlienEvent;
import alien4cloud.paas.cloudify2.events.NodeInstanceState;
import alien4cloud.paas.cloudify2.exception.PaaSEventException;
import alien4cloud.rest.utils.JsonUtil;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

@Slf4j
public class CloudifyEventsListener {

    private static final String GET_EVENTS_END_POINT = "/events/getEvents";

    private static final String SERVICE_KEY = "service";

    private static final String APPLICATION_KEY = "application";

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

    private String doGet(URIBuilder builder, boolean failOnError) throws URISyntaxException, IOException {
        ResponseStatus response = doGetStatus(builder);
        if (response.status == 200) {
            return response.response;
        }
        log.debug("Failed to execute" + builder.build() + ". Status is: " + response.status + "\n\tResponse is: " + response.response);
        if (failOnError) {
            throw new PaaSEventException("Failed to execute " + builder.build() + ". Status is: " + response.status);
        } else {
            log.warn("Failed to execute " + builder.build() + ". Status is: " + response.status);
            return null;
        }
    }

    private ResponseStatus doGetStatus(URIBuilder builder) throws URISyntaxException, IOException {
        URI uri = builder.build();
        log.debug("Query uri {}", uri);
        HttpGet request = new HttpGet(builder.build());
        ResponseStatus response = execRequest(request);
        return response;
    }

    private ResponseStatus execRequest(HttpRequestBase request) throws URISyntaxException, IOException {
        HttpResponse httpResponse = httpClient.execute(request);
        ResponseStatus responseStatus = new ResponseStatus(httpResponse.getStatusLine().getStatusCode(), EntityUtils.toString(httpResponse.getEntity()));
        return responseStatus;
    }

    private String doDelete(URIBuilder builder) throws URISyntaxException, IOException {
        URI uri = builder.build();
        log.debug("Query uri {}", uri);
        HttpDelete request = new HttpDelete(builder.build());
        ResponseStatus response = execRequest(request);
        return response.response;
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
        ResponseStatus response = doGetStatus(builder);
        if (response.status != 200) {
            throw new IOException("Failed to connect to event endpoint" + builder.build() + ". Status is: " + response.status + "; Response is: "
                    + response.response);
        }
        return response.response;
    }

    public List<AlienEvent> getEvents() throws IOException, URISyntaxException {
        URIBuilder builder = new URIBuilder(endpoint.resolve(GET_EVENTS_END_POINT)).addParameter(APPLICATION_KEY, application)
                .addParameter(SERVICE_KEY, service).addParameter("lastIndex", "0");

        String response = doGet(builder, true);
        return new ObjectMapper().readValue(response, new TypeReference<List<AlienEvent>>() {
        });
        // return JsonUtil.toList(response, AlienEvent.class);
    }

    public List<AlienEvent> getEventsSince(Date date, int maxEvents) throws URISyntaxException, IOException {
        long dateAsLong = date.getTime();

        URIBuilder builder = new URIBuilder(endpoint.resolve("/events/getEventsSince")).addParameter("dateAsLong", Long.toString(dateAsLong)).addParameter(
                "maxEvents", Integer.toString(maxEvents));

        String response = this.doGet(builder, false);

        if (StringUtils.isNotBlank(response)) {
            return new ObjectMapper().readValue(response, new TypeReference<List<AlienEvent>>() {
            });
        } else {
            return Lists.<AlienEvent> newArrayList();
        }
    }

    public List<NodeInstanceState> getNodeInstanceStates(String topologyId) throws URISyntaxException, IOException {
        URIBuilder builder = new URIBuilder(endpoint.resolve("/events/getInstanceStates")).addParameter(APPLICATION_KEY, topologyId);

        String response = this.doGet(builder, false);
        return StringUtils.isNotBlank(response) ? JsonUtil.toList(response, NodeInstanceState.class) : Lists.<NodeInstanceState> newArrayList();
    }

    public void deleteNodeInstanceStates(String topologyId) throws URISyntaxException, IOException {
        URIBuilder builder = new URIBuilder(endpoint.resolve("/events/deleteInstanceStates")).addParameter(APPLICATION_KEY, topologyId);
        this.doDelete(builder);
    }

    private class ResponseStatus {
        private int status;
        private String response;

        ResponseStatus(int code, String response) {
            this.status = code;
            this.response = response;
        }
    }
}
