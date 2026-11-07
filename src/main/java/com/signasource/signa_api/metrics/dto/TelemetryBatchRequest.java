package com.signasource.signa_api.metrics.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** A batch of client telemetry events, posted periodically by the web and mobile clients. */
public record TelemetryBatchRequest(
        @NotNull @Size(max = TelemetryBatchRequest.MAX_EVENTS) List<TelemetryEventRequest> events) {

    /** Hard cap per request so a single call cannot flood the metric pipeline. */
    public static final int MAX_EVENTS = 50;
}
