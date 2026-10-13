package com.signasource.signa_api.admin.controller;

import com.signasource.signa_api.admin.dto.AdminSignReportGroupResponse;
import com.signasource.signa_api.admin.dto.UpdateSignReportGroupRequest;
import com.signasource.signa_api.admin.service.AdminService;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.learning.entity.ReportStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<List<AdminSignReportGroupResponse>> list(
            @RequestParam(required = false) ReportStatus status) {
        return ResponseEntity.ok(adminService.getSignReportGroups(status));
    }

    @PatchMapping("/signs/{signId}")
    public ResponseEntity<AdminSignReportGroupResponse> updateGroup(
            @PathVariable UUID signId,
            @Valid @RequestBody UpdateSignReportGroupRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                adminService.updateSignReportGroup(signId, request, userDetails.getUser()));
    }
}
