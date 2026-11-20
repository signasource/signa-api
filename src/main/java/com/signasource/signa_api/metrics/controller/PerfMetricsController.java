package com.signasource.signa_api.metrics.controller;

import com.signasource.signa_api.metrics.dto.PerfMetricsResponse;
import com.signasource.signa_api.metrics.service.MetricsRange;
import com.signasource.signa_api.metrics.service.PerfMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Performance/infra widgets for the admin usage dashboard, proxied from Grafana Cloud (ADMIN only).
 * The {@code widget} path variable names an entry in the fixed {@code PerfWidget} catalog.
 */
@RestController
@RequestMapping("/admin/metrics/perf")
@RequiredArgsConstructor
public class PerfMetricsController {

    private static final long DEFAULT_STEP_SECONDS = 60;

    private final PerfMetricsService perfMetricsService;

    @GetMapping("/{widget}")
    public ResponseEntity<PerfMetricsResponse> perf(
            @PathVariable String widget,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long step) {
        MetricsRange range = MetricsRange.fromIso(from, to);
        long stepSeconds = step == null || step <= 0 ? DEFAULT_STEP_SECONDS : step;
        return ResponseEntity.ok(perfMetricsService.widget(widget, range, stepSeconds));
    }
}
