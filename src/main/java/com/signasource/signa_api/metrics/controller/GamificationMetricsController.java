package com.signasource.signa_api.metrics.controller;

import com.signasource.signa_api.metrics.dto.GamificationMetricsResponse;
import com.signasource.signa_api.metrics.service.GamificationMetricsService;
import com.signasource.signa_api.metrics.service.MetricsRange;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Global gamification & engagement metrics for the admin usage dashboard (ADMIN only, enforced by
 * SecurityConfig via the {@code /admin/**} matcher).
 */
@RestController
@RequestMapping("/admin/metrics/gamification")
@RequiredArgsConstructor
public class GamificationMetricsController {

    private final GamificationMetricsService gamificationMetricsService;

    @GetMapping
    public ResponseEntity<GamificationMetricsResponse> gamification(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(
                gamificationMetricsService.getGamificationMetrics(MetricsRange.fromIso(from, to)));
    }
}
