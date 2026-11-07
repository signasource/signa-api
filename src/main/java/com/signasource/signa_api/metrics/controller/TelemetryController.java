package com.signasource.signa_api.metrics.controller;

import com.signasource.signa_api.metrics.dto.TelemetryBatchRequest;
import com.signasource.signa_api.metrics.service.TelemetryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public ingestion endpoint for web/mobile telemetry (web vitals, screen/animation load times,
 * client errors). Always answers 202: clients fire-and-forget and must never retry on a 4xx/5xx.
 * Rate limited per IP in {@code WebConfig}; cardinality is bounded in {@code TelemetryService}.
 */
@RestController
@RequestMapping("/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryService telemetryService;

    @PostMapping("/events")
    public ResponseEntity<Void> ingest(@Valid @RequestBody TelemetryBatchRequest batch) {
        telemetryService.record(batch);
        return ResponseEntity.accepted().build();
    }
}
