package com.signasource.signa_api.admin.controller;

import com.signasource.signa_api.admin.dto.AdminSignReportResponse;
import com.signasource.signa_api.admin.dto.UpdateSignReportRequest;
import com.signasource.signa_api.admin.service.AdminService;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.learning.entity.ReportReason;
import com.signasource.signa_api.learning.entity.ReportStatus;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/sign-reports")
@RequiredArgsConstructor
public class AdminSignReportController {

    private final AdminService adminService;

    @GetMapping
    public ResponseEntity<List<AdminSignReportResponse>> list(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) ReportReason reason,
            @RequestParam(required = false) String signQuery,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        return ResponseEntity.ok(adminService.getSignReports(status, reason, signQuery, from, to));
    }

    @PatchMapping("/{reportId}")
    public ResponseEntity<AdminSignReportResponse> update(
            @PathVariable UUID reportId,
            @Valid @RequestBody UpdateSignReportRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                adminService.updateSignReport(reportId, request, userDetails.getUser()));
    }
}
