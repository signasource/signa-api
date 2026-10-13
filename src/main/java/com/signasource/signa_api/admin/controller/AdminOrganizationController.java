package com.signasource.signa_api.admin.controller;

import com.signasource.signa_api.admin.dto.AdminOrganizationResponse;
import com.signasource.signa_api.admin.dto.UpdateOrganizationStatusRequest;
import com.signasource.signa_api.admin.service.AdminService;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/organizations")
@RequiredArgsConstructor
public class AdminOrganizationController {

    private final AdminService adminService;

    @GetMapping
    public ResponseEntity<Page<AdminOrganizationResponse>> list(
            @RequestParam(required = false) List<OrganizationStatus> status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminService.getOrganizations(status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminOrganizationResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getOrganization(id));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AdminOrganizationResponse> updateStatus(
            @PathVariable UUID id, @Valid @RequestBody UpdateOrganizationStatusRequest request) {
        return ResponseEntity.ok(adminService.updateOrganizationStatus(id, request));
    }
}
