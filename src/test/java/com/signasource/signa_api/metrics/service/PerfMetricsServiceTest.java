package com.signasource.signa_api.metrics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.metrics.client.GrafanaQueryClient;
import com.signasource.signa_api.metrics.dto.PerfMetricsResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PerfMetricsServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private GrafanaQueryClient client;
    @InjectMocks private PerfMetricsService service;

    private final MetricsRange range =
            MetricsRange.of(
                    Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-01T01:00:00Z"));

    @Test
    void unknownWidgetIsNotFound() {
        assertThrows(NotFoundException.class, () -> service.widget("nope", range, 60));
    }

    @Test
    void unavailableWhenTokenMissing() {
        when(client.available()).thenReturn(false);

        PerfMetricsResponse response = service.widget("latency_p95", range, 60);

        assertFalse(response.available());
        assertTrue(response.series().isEmpty());
    }

    @Test
    void parsesMatrixResult() throws Exception {
        when(client.available()).thenReturn(true);
        JsonNode body =
                MAPPER.readTree(
                        "{\"status\":\"success\",\"data\":{\"resultType\":\"matrix\","
                                + "\"result\":[{\"metric\":{\"uri\":\"/x\"},"
                                + "\"values\":[[1699,\"0.5\"],[1700,\"NaN\"]]}]}}");
        when(client.queryRange(any(), any(), any(), anyLong())).thenReturn(body);

        PerfMetricsResponse response = service.widget("latency_p95", range, 60);

        assertTrue(response.available());
        assertEquals(1, response.series().size());
        assertEquals("/x", response.series().get(0).labels().get("uri"));
        assertEquals(0.5, response.series().get(0).points().get(0).v());
        assertEquals(null, response.series().get(0).points().get(1).v());
    }

    @Test
    void readFailureDegradesToEmptySeries() {
        when(client.available()).thenReturn(true);
        when(client.queryRange(any(), any(), any(), anyLong()))
                .thenThrow(new RuntimeException("grafana down"));

        PerfMetricsResponse response = service.widget("cpu_host", range, 60);

        assertTrue(response.available());
        assertTrue(response.series().isEmpty());
    }
}
