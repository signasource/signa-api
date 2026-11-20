package com.signasource.signa_api.metrics.controller;

import com.signasource.signa_api.metrics.dto.MlMetricsResponse;
import com.signasource.signa_api.metrics.service.MetricsRange;
import com.signasource.signa_api.metrics.service.MlMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exposes on-device ML / camera-recognition metrics for the usage dashboard. */
@RestController
@RequestMapping("/admin/metrics/ml")
@RequiredArgsConstructor
public class MlMetricsController {

    private final MlMetricsService mlMetricsService;

    @GetMapping
    public ResponseEntity<MlMetricsResponse> ml(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(mlMetricsService.getMlMetrics(MetricsRange.fromIso(from, to)));
    }
}
