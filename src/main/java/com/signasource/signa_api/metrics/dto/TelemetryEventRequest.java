package com.signasource.signa_api.metrics.dto;

/**
 * One client telemetry event. All fields are advisory: {@code TelemetryService} validates them
 * against bounded allow-lists and silently drops anything unknown, so a malicious or buggy client
 * can never create unbounded Prometheus series.
 *
 * @param platform web / android / ios
 * @param type web_vital / screen_load / animation_load / client_error / api_error
 * @param name the metric detail (vital name, screen name, error category); ignored for
 *     animation_load to keep cardinality bounded
 * @param valueMs duration or value in milliseconds (null for pure error counters)
 * @param status ok / error (used by animation_load)
 */
public record TelemetryEventRequest(
        String platform, String type, String name, Double valueMs, String status) {}
