package com.signasource.signa_api.metrics.controller;

import com.signasource.signa_api.metrics.dto.BusinessMetricsResponse;
import com.signasource.signa_api.metrics.service.GlobalMetricsService;
import com.signasource.signa_api.metrics.service.MetricsRange;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Global business metrics for the admin usage dashboard (ADMIN only, enforced by SecurityConfig).
 */
@RestController
@RequestMapping("/admin/metrics/business")
@RequiredArgsConstructor
public class BusinessMetricsController {

    private final GlobalMetricsService globalMetricsService;

    @GetMapping
    public ResponseEntity<BusinessMetricsResponse> business(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(
                globalMetricsService.getBusinessMetrics(MetricsRange.fromIso(from, to)));
    }
}
