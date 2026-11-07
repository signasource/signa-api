package com.signasource.signa_api.metrics.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.metrics.dto.BusinessMetricsResponse;
import com.signasource.signa_api.metrics.dto.PerfMetricsResponse;
import com.signasource.signa_api.metrics.dto.TelemetryBatchRequest;
import com.signasource.signa_api.metrics.service.GlobalMetricsService;
import com.signasource.signa_api.metrics.service.MetricsRange;
import com.signasource.signa_api.metrics.service.PerfMetricsService;
import com.signasource.signa_api.metrics.service.TelemetryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class MetricsControllersTest {

    @Mock private GlobalMetricsService globalMetricsService;
    @Mock private PerfMetricsService perfMetricsService;
    @Mock private TelemetryService telemetryService;

    @Test
    void businessDelegatesWithParsedRange() {
        BusinessMetricsController controller = new BusinessMetricsController(globalMetricsService);
        BusinessMetricsResponse body =
                new BusinessMetricsResponse(null, List.of(), null, null, null, null, List.of());
        when(globalMetricsService.getBusinessMetrics(any())).thenReturn(body);

        ResponseEntity<BusinessMetricsResponse> response =
                controller.business("2026-11-01T00:00:00Z", "2026-11-02T00:00:00Z");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(body, response.getBody());
    }

    @Test
    void perfDefaultsStepWhenMissing() {
        PerfMetricsController controller = new PerfMetricsController(perfMetricsService);
        when(perfMetricsService.widget(eq("latency_p95"), any(MetricsRange.class), eq(60L)))
                .thenReturn(PerfMetricsResponse.unavailable("latency_p95"));

        ResponseEntity<PerfMetricsResponse> response =
                controller.perf(
                        "latency_p95", "2026-11-01T00:00:00Z", "2026-11-01T01:00:00Z", null);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(perfMetricsService).widget(eq("latency_p95"), any(MetricsRange.class), eq(60L));
    }

    @Test
    void perfUsesRequestedStep() {
        PerfMetricsController controller = new PerfMetricsController(perfMetricsService);
        when(perfMetricsService.widget(any(), any(), eq(300L)))
                .thenReturn(PerfMetricsResponse.unavailable("cpu_host"));

        controller.perf("cpu_host", "2026-11-01T00:00:00Z", "2026-11-01T02:00:00Z", 300L);

        verify(perfMetricsService).widget(eq("cpu_host"), any(MetricsRange.class), eq(300L));
    }

    @Test
    void telemetryAnswers202AndRecords() {
        TelemetryController controller = new TelemetryController(telemetryService);
        TelemetryBatchRequest batch = new TelemetryBatchRequest(List.of());

        ResponseEntity<Void> response = controller.ingest(batch);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        ArgumentCaptor<TelemetryBatchRequest> captor =
                ArgumentCaptor.forClass(TelemetryBatchRequest.class);
        verify(telemetryService).record(captor.capture());
        assertEquals(batch, captor.getValue());
    }
}
