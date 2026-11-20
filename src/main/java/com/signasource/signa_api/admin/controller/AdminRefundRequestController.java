package com.signasource.signa_api.admin.controller;

import com.signasource.signa_api.admin.dto.AdminRefundRequestResponse;
import com.signasource.signa_api.admin.dto.UpdateRefundRequestRequest;
import com.signasource.signa_api.admin.service.AdminRefundRequestService;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/refund-requests")
@RequiredArgsConstructor
public class AdminRefundRequestController {

    private final AdminRefundRequestService service;

    @GetMapping
    public ResponseEntity<List<AdminRefundRequestResponse>> list() {
        return ResponseEntity.ok(service.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminRefundRequestResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<AdminRefundRequestResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRefundRequestRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String adminName =
                userDetails.getUser().getName() + " " + userDetails.getUser().getLastName();
        return ResponseEntity.ok(service.update(id, request, adminName));
    }
}
