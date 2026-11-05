package com.signasource.signa_api.metrics.service;

import com.signasource.signa_api.common.MetricsService;
import com.signasource.signa_api.metrics.dto.TelemetryBatchRequest;
import com.signasource.signa_api.metrics.dto.TelemetryEventRequest;
import java.time.Duration;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Turns client telemetry events into Micrometer metrics. Every tag value is validated against a
 * closed allow-list before it reaches the registry: unbounded tags (a raw screen name, a sign
 * meaning, a free-text error) would explode Prometheus series and run up the Grafana Cloud bill.
 * Unknown platforms are dropped; unknown names fall back to {@code "other"}.
 */
@Service
@RequiredArgsConstructor
public class TelemetryService {

    private static final Set<String> PLATFORMS = Set.of("web", "android", "ios");
    private static final Set<String> WEB_VITALS = Set.of("LCP", "CLS", "INP", "TTFB", "FCP");
    private static final Set<String> SCREENS =
            Set.of("home", "lesson", "practice", "profile", "store", "course", "landing", "other");
    private static final Set<String> ERROR_CATEGORIES =
            Set.of("network", "render", "http_4xx", "http_5xx", "unknown");
    private static final String OTHER = "other";
    private static final String UNKNOWN = "unknown";

    private final MetricsService metricsService;

    public void record(TelemetryBatchRequest batch) {
        if (batch == null || batch.events() == null) {
            return;
        }
        for (TelemetryEventRequest event : batch.events()) {
            try {
                recordEvent(event);
            } catch (RuntimeException ignored) {
                // A single malformed event never fails the batch (clients fire-and-forget).
            }
        }
    }

    private void recordEvent(TelemetryEventRequest event) {
        if (event == null || event.type() == null) {
            return;
        }
        String platform = normalize(event.platform());
        if (!PLATFORMS.contains(platform)) {
            return;
        }
        switch (event.type()) {
            case "web_vital" -> {
                String name = event.name();
                if (name != null && WEB_VITALS.contains(name)) {
                    metricsService.recordClientWebVital(platform, name, millis(event.valueMs()));
                }
            }
            case "screen_load" ->
                    metricsService.recordClientScreenLoad(
                            platform,
                            allowed(event.name(), SCREENS, OTHER),
                            millis(event.valueMs()));
            case "animation_load" ->
                    metricsService.recordClientAnimationLoad(
                            platform, !"error".equals(event.status()), millis(event.valueMs()));
            case "client_error", "api_error" ->
                    metricsService.recordClientError(
                            platform, allowed(event.name(), ERROR_CATEGORIES, UNKNOWN));
            case "ml_inference" ->
                    metricsService.recordClientInference(platform, millis(event.valueMs()));
            default -> {
                // Unknown event type: ignore.
            }
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }

    private static String allowed(String value, Set<String> allowList, String fallback) {
        return value != null && allowList.contains(value) ? value : fallback;
    }

    private static Duration millis(Double valueMs) {
        if (valueMs == null || valueMs < 0 || valueMs.isNaN() || valueMs.isInfinite()) {
            return Duration.ZERO;
        }
        return Duration.ofNanos((long) (valueMs * 1_000_000L));
    }
}
