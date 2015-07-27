package alien4cloud.paas.cloudify2.rest;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import alien4cloud.paas.cloudify2.exception.PaaSEventException;
import alien4cloud.rest.utils.JsonUtil;

@AllArgsConstructor
@Setter
@Slf4j
@Component("cloudify-rest-executor-bean")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class RestExecutor {

    private DefaultHttpClient httpClient;

    public RestExecutor() {
        PoolingClientConnectionManager cm = new PoolingClientConnectionManager();
        cm.setMaxTotal(200);
        cm.setDefaultMaxPerRoute(20);
        this.httpClient = new DefaultHttpClient(cm);
    }

    @PostConstruct
    public void postConstruct() {
        log.info("Instantiated rest executor " + this);
    }

    @PreDestroy
    public void shutDown() {
        this.httpClient.getConnectionManager().shutdown();
    }

    public String doGet(URIBuilder builder, boolean failOnError) throws URISyntaxException, IOException {
        Response response = doGet(builder);
        return parseResponse(builder, response, failOnError);
    }

    public String doPost(URIBuilder builder, Object requestBody, boolean failOnError) throws URISyntaxException, IOException {
        Response response = doPostWithStatus(builder, requestBody);
        return parseResponse(builder, response, failOnError);
    }

    private Response execRequest(HttpRequestBase request) throws URISyntaxException, IOException {
        HttpResponse httpResponse = httpClient.execute(request);
        try {
            return new Response(httpResponse.getStatusLine().getStatusCode(), httpResponse.getStatusLine().getReasonPhrase(), EntityUtils.toString(httpResponse
                    .getEntity()));
        } finally {
            if (httpResponse != null) {
                EntityUtils.consumeQuietly(httpResponse.getEntity());
            }
        }
    }

    private Response doGet(URIBuilder builder) throws URISyntaxException, IOException {
        log.debug("Query uri {}", builder.build());
        HttpGet request = new HttpGet(builder.build());
        Response response = execRequest(request);
        return response;
    }

    public String doDelete(URIBuilder builder) throws URISyntaxException, IOException {
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

    @Getter
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
