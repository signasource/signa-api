package com.signasource.signa_api.metrics.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.signasource.signa_api.metrics.config.GrafanaProperties;
import java.net.URI;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Thin client over Grafana Cloud's Prometheus {@code query_range} API. The PromQL is built from the
 * fixed {@code PerfWidget} catalog and passed as a pre-encoded URI (never a URI template) because
 * PromQL's {@code {label="x"}} braces would otherwise be misread as template variables.
 */
@Component
@RequiredArgsConstructor
public class GrafanaQueryClient {

    private final RestClient grafanaRestClient;
    private final GrafanaProperties properties;

    public boolean available() {
        return properties.configured();
    }

    public JsonNode queryRange(String promQl, Instant start, Instant end, long stepSeconds) {
        URI uri =
                UriComponentsBuilder.fromUriString(properties.queryUrl() + "/api/v1/query_range")
                        .queryParam("query", promQl)
                        .queryParam("start", start.getEpochSecond())
                        .queryParam("end", end.getEpochSecond())
                        .queryParam("step", stepSeconds + "s")
                        .build()
                        .encode()
                        .toUri();

        return grafanaRestClient
                .get()
                .uri(uri)
                .headers(h -> h.setBasicAuth(properties.user(), properties.token()))
                .retrieve()
                .body(JsonNode.class);
    }
}
