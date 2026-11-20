package com.signasource.signa_api.metrics.controller;

import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse;
import com.signasource.signa_api.metrics.service.GrowthMetricsService;
import com.signasource.signa_api.metrics.service.MetricsRange;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Global growth & business metrics for the admin usage dashboard (ADMIN only, enforced by
 * SecurityConfig via the {@code /admin/**} matcher).
 */
@RestController
@RequestMapping("/admin/metrics/growth")
@RequiredArgsConstructor
public class GrowthMetricsController {

    private final GrowthMetricsService growthMetricsService;

    @GetMapping
    public ResponseEntity<GrowthMetricsResponse> growth(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(
                growthMetricsService.getGrowthMetrics(MetricsRange.fromIso(from, to)));
    }
}
