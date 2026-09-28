package com.signasource.signa_api.organizations.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.organizations.dto.ModuleStatsResponse;
import com.signasource.signa_api.organizations.dto.OrganizationOverviewResponse;
import com.signasource.signa_api.organizations.service.OrganizationMetricsService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/organizations/{organizationId}")
@RequiredArgsConstructor
public class OrganizationMetricsController {

    private final OrganizationMetricsService metricsService;

    @GetMapping("/overview")
    public ResponseEntity<OrganizationOverviewResponse> getOverview(
            @PathVariable UUID organizationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(metricsService.getOverview(userDetails.getUser(), organizationId));
    }

    @GetMapping("/modules")
    public ResponseEntity<List<ModuleStatsResponse>> getModuleStats(
            @PathVariable UUID organizationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                metricsService.getModuleStats(userDetails.getUser(), organizationId));
    }
}
