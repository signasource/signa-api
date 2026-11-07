package com.signasource.signa_api.metrics.dto;

import java.util.List;
import java.util.Map;

/**
 * A performance widget's time series, proxied from Grafana Cloud Prometheus. {@code available} is
 * false when the Grafana read token is not configured, so the client can show "sin datos" instead
 * of an error.
 */
public record PerfMetricsResponse(String widget, boolean available, List<Series> series) {

    /** One Prometheus series: its label set and its points over the window. */
    public record Series(Map<String, String> labels, List<Point> points) {}

    /** One sample: epoch seconds and the value (null when Prometheus reported NaN). */
    public record Point(double t, Double v) {}

    public static PerfMetricsResponse unavailable(String widget) {
        return new PerfMetricsResponse(widget, false, List.of());
    }
}
