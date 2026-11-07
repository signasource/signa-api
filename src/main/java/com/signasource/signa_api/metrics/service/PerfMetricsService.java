package com.signasource.signa_api.metrics.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.signasource.signa_api.metrics.client.GrafanaQueryClient;
import com.signasource.signa_api.metrics.dto.PerfMetricsResponse;
import com.signasource.signa_api.metrics.dto.PerfMetricsResponse.Point;
import com.signasource.signa_api.metrics.dto.PerfMetricsResponse.Series;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Resolves a performance widget to PromQL and proxies it to Grafana Cloud for the dashboard. */
@Service
@RequiredArgsConstructor
@Slf4j
public class PerfMetricsService {

    /** Prometheus rejects a range query with more than 11000 points; stay well under it. */
    private static final long MAX_POINTS = 1000;

    private static final long MIN_STEP_SECONDS = 15;
    private static final long MIN_RATE_WINDOW_SECONDS = 60;
    private static final Duration DEFAULT_WINDOW = Duration.ofHours(1);

    private final GrafanaQueryClient grafanaQueryClient;

    public PerfMetricsResponse widget(String key, MetricsRange range, long requestedStepSeconds) {
        PerfWidget widget = PerfWidget.fromKey(key);
        if (!grafanaQueryClient.available()) {
            return PerfMetricsResponse.unavailable(key);
        }

        Instant end = range.end();
        Instant start = range.isOpen() ? end.minus(DEFAULT_WINDOW) : range.start();
        long stepSeconds = resolveStep(requestedStepSeconds, start, end);
        String rateWindow = Math.max(stepSeconds, MIN_RATE_WINDOW_SECONDS) + "s";

        try {
            JsonNode root =
                    grafanaQueryClient.queryRange(
                            widget.promQl(rateWindow), start, end, stepSeconds);
            return new PerfMetricsResponse(key, true, parse(root));
        } catch (RuntimeException ex) {
            // A read failure must not break the dashboard: the business panels still render.
            log.warn("Grafana query failed for widget {}: {}", key, ex.getMessage());
            return new PerfMetricsResponse(key, true, List.of());
        }
    }

    private long resolveStep(long requested, Instant start, Instant end) {
        long duration = Math.max(1, end.getEpochSecond() - start.getEpochSecond());
        long minStep = Math.max(MIN_STEP_SECONDS, (long) Math.ceil((double) duration / MAX_POINTS));
        return Math.max(requested, minStep);
    }

    private static List<Series> parse(JsonNode root) {
        List<Series> series = new ArrayList<>();
        if (root == null) {
            return series;
        }
        for (JsonNode node : root.path("data").path("result")) {
            Map<String, String> labels = new LinkedHashMap<>();
            node.path("metric")
                    .fields()
                    .forEachRemaining(e -> labels.put(e.getKey(), e.getValue().asText()));
            List<Point> points = new ArrayList<>();
            for (JsonNode value : node.path("values")) {
                if (value.size() < 2) {
                    continue;
                }
                points.add(new Point(value.get(0).asDouble(), parseValue(value.get(1).asText())));
            }
            series.add(new Series(labels, points));
        }
        return series;
    }

    private static Double parseValue(String raw) {
        try {
            double parsed = Double.parseDouble(raw);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
