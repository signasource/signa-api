package com.signasource.signa_api.metrics.controller;

import com.signasource.signa_api.metrics.dto.LearningMetricsResponse;
import com.signasource.signa_api.metrics.service.LearningMetricsService;
import com.signasource.signa_api.metrics.service.MetricsRange;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Global learning & content metrics for the admin usage dashboard (ADMIN only, enforced by
 * SecurityConfig via the {@code /admin/**} matcher).
 */
@RestController
@RequestMapping("/admin/metrics/learning")
@RequiredArgsConstructor
public class LearningMetricsController {

    private final LearningMetricsService learningMetricsService;

    @GetMapping
    public ResponseEntity<LearningMetricsResponse> learning(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) List<UUID> courseIds) {
        return ResponseEntity.ok(
                learningMetricsService.getLearningMetrics(
                        MetricsRange.fromIso(from, to), courseIds != null ? courseIds : List.of()));
    }
}
