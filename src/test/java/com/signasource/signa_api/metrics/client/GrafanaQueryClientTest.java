package com.signasource.signa_api.metrics.client;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.signasource.signa_api.metrics.config.GrafanaProperties;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GrafanaQueryClientTest {

    @Test
    void availabilityFollowsProperties() {
        GrafanaProperties none = new GrafanaProperties("", "", "");
        GrafanaProperties full = new GrafanaProperties("https://prom/api/prom", "123", "tok");
        assertFalse(new GrafanaQueryClient(RestClient.create(), none).available());
        assertTrue(new GrafanaQueryClient(RestClient.create(), full).available());
    }

    @Test
    void queryRangeHitsPrometheusApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GrafanaProperties props = new GrafanaProperties("https://prom/api/prom", "123", "tok");
        GrafanaQueryClient client = new GrafanaQueryClient(builder.build(), props);

        server.expect(requestTo(containsString("/api/prom/api/v1/query_range")))
                .andExpect(requestTo(containsString("step=60s")))
                .andExpect(method(HttpMethod.GET))
                .andRespond(
                        withSuccess(
                                "{\"status\":\"success\",\"data\":{\"result\":[]}}",
                                MediaType.APPLICATION_JSON));

        JsonNode body =
                client.queryRange(
                        "up{job=\"signa-api\"}",
                        Instant.ofEpochSecond(100),
                        Instant.ofEpochSecond(160),
                        60);

        assertEquals("success", body.path("status").asText());
        server.verify();
    }
}
