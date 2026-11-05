package com.signasource.signa_api.metrics.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.signasource.signa_api.common.MetricsService;
import com.signasource.signa_api.metrics.dto.TelemetryBatchRequest;
import com.signasource.signa_api.metrics.dto.TelemetryEventRequest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelemetryServiceTest {

    @Mock private MetricsService metricsService;
    @InjectMocks private TelemetryService telemetryService;

    private static TelemetryBatchRequest batch(TelemetryEventRequest... events) {
        return new TelemetryBatchRequest(List.of(events));
    }

    @Test
    void recordsKnownWebVital() {
        telemetryService.record(
                batch(new TelemetryEventRequest("web", "web_vital", "LCP", 2500.0, "ok")));

        verify(metricsService).recordClientWebVital(eq("web"), eq("LCP"), any(Duration.class));
    }

    @Test
    void dropsUnknownWebVitalName() {
        telemetryService.record(
                batch(new TelemetryEventRequest("web", "web_vital", "MADE_UP", 1.0, "ok")));

        verifyNoInteractions(metricsService);
    }

    @Test
    void bucketsUnknownScreenToOther() {
        telemetryService.record(
                batch(
                        new TelemetryEventRequest(
                                "android", "screen_load", "secret-screen", 120.0, "ok")));

        verify(metricsService)
                .recordClientScreenLoad(eq("android"), eq("other"), any(Duration.class));
    }

    @Test
    void animationLoadUsesStatusForResult() {
        telemetryService.record(
                batch(
                        new TelemetryEventRequest(
                                "android", "animation_load", "hola", 800.0, "error")));

        verify(metricsService)
                .recordClientAnimationLoad(eq("android"), eq(false), any(Duration.class));
    }

    @Test
    void errorFallsBackToUnknownCategory() {
        telemetryService.record(
                batch(new TelemetryEventRequest("web", "client_error", "weird", null, "error")));

        verify(metricsService).recordClientError("web", "unknown");
    }

    @Test
    void recordsMlInferenceLatency() {
        telemetryService.record(
                batch(new TelemetryEventRequest("android", "ml_inference", null, 45.0, "ok")));

        verify(metricsService).recordClientInference(eq("android"), any(Duration.class));
    }

    @Test
    void dropsUnknownPlatform() {
        telemetryService.record(
                batch(new TelemetryEventRequest("desktop", "web_vital", "LCP", 1.0, "ok")));

        verifyNoInteractions(metricsService);
    }

    @Test
    void ignoresNullBatch() {
        telemetryService.record(null);
        telemetryService.record(new TelemetryBatchRequest(null));

        verifyNoInteractions(metricsService);
    }

    @Test
    void negativeValueClampsToZeroWithoutFailing() {
        telemetryService.record(
                batch(new TelemetryEventRequest("ios", "screen_load", "home", -5.0, "ok")));

        verify(metricsService).recordClientScreenLoad(eq("ios"), eq("home"), eq(Duration.ZERO));
    }
}
