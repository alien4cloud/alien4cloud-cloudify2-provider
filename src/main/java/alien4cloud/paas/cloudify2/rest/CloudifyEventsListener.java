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
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.http.MediaType;

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

    private static final String DEPLOYMENT_ID_KEY = "deploymentId";

    private DefaultHttpClient httpClient;
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
        this.httpClient = new DefaultHttpClient();
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
        Response response = doGetWithStatus(builder);
        if (response.status != 200) {
            log.debug("Failed to execute" + builder.build() + ". Status: " + response.status + " ; Reason: " + response.errorReason + "\n\tResponse is: ");
            throw new IOException("Failed to execute " + builder.build() + ". Status: " + response.status + " ; Reason: " + response.errorReason);
        }
        return response.response;
    }

    public List<AlienEvent> getEvents() throws IOException, URISyntaxException {
        URIBuilder builder = new URIBuilder(endpoint.resolve(GET_EVENTS_END_POINT)).addParameter(DEPLOYMENT_ID_KEY, deploymentId)
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

    public List<NodeInstanceState> getNodeInstanceStates(String deploymentId) throws URISyntaxException, IOException {
        URIBuilder builder = new URIBuilder(endpoint.resolve("/events/getInstanceStates")).addParameter(DEPLOYMENT_ID_KEY, deploymentId);

        String response = this.doGet(builder, false);
        return StringUtils.isNotBlank(response) ? JsonUtil.toList(response, NodeInstanceState.class) : Lists.<NodeInstanceState> newArrayList();
    }

    public void deleteNodeInstanceStates(String deploymentId) throws URISyntaxException, IOException {
        URIBuilder builder = new URIBuilder(endpoint.resolve("/events/deleteInstanceStates")).addParameter(DEPLOYMENT_ID_KEY, deploymentId);
        this.doDelete(builder);
    }

    public void putNodeInstanceStates(List<NodeInstanceState> nodeInstanceStates) throws URISyntaxException, IOException {
        URIBuilder builder = new URIBuilder(endpoint.resolve("/events/putInstanceStates"));
        this.doPost(builder, nodeInstanceStates, true);
    }

    private String doGet(URIBuilder builder, boolean failOnError) throws URISyntaxException, IOException {
        Response response = doGetWithStatus(builder);
        return parseResponse(builder, response, failOnError);
    }

    private Response doGetWithStatus(URIBuilder builder) throws URISyntaxException, IOException {
        log.debug("Query uri {}", builder.build());
        HttpGet request = new HttpGet(builder.build());
        Response response = execRequest(request);
        return response;
    }

    private String doDelete(URIBuilder builder) throws URISyntaxException, IOException {
        log.debug("Query uri {}", builder.build());
        HttpDelete request = new HttpDelete(builder.build());
        Response response = execRequest(request);
        return response.response;
    }

    private Response doPostWithStatus(URIBuilder builder, Object requestBody) throws URISyntaxException, IOException {
        log.debug("Query uri {}", builder.build());
        HttpPost request = new HttpPost(builder.build());
        if (requestBody != null) {
            StringEntity jsonEntity = new StringEntity(JsonUtil.toString(requestBody));
            jsonEntity.setContentType(MediaType.APPLICATION_JSON_VALUE);
            request.setEntity(jsonEntity);
        }
        Response response = execRequest(request);
        return response;
    }

    private String doPost(URIBuilder builder, Object requestBody, boolean failOnError) throws URISyntaxException, IOException {
        Response response = doPostWithStatus(builder, requestBody);
        return parseResponse(builder, response, failOnError);
    }

    private String parseResponse(URIBuilder builder, Response response, boolean failOnError) throws URISyntaxException {
        if (response.status == 200) {
            return response.response;
        }
        log.debug("Failed to execute" + builder.build() + ". Status: " + response.status + " ; Reason: " + response.errorReason + "\n\tResponse is: "
                + response.response);
        if (failOnError) {
            throw new PaaSEventException("Failed to execute " + builder.build() + ". Status: " + response.status + " ; Reason: " + response.errorReason);
        } else {
            log.warn("Failed to execute " + builder.build() + ". Status: " + response.status + " ; Reason: " + response.errorReason);
            return null;
        }
    }

    private Response execRequest(HttpRequestBase request) throws URISyntaxException, IOException {
        HttpResponse httpResponse = httpClient.execute(request);
        return new Response(httpResponse.getStatusLine().getStatusCode(), httpResponse.getStatusLine().getReasonPhrase(), EntityUtils.toString(httpResponse
                .getEntity()));
    }

    private class Response {
        private int status;
        private String errorReason;
        private String response;

        Response(int code, String errorReason, String response) {
            this.status = code;
            this.errorReason = errorReason;
            this.response = response;
        }
    }
}
