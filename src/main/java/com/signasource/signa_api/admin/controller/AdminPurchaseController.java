package com.signasource.signa_api.admin.controller;

import com.signasource.signa_api.admin.dto.AdminPurchaseDetailResponse;
import com.signasource.signa_api.admin.dto.AdminPurchaseListItemResponse;
import com.signasource.signa_api.admin.dto.ConfirmPaymentRequest;
import com.signasource.signa_api.admin.service.AdminPurchasesService;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/purchases")
@RequiredArgsConstructor
public class AdminPurchaseController {

    private final AdminPurchasesService service;

    @GetMapping
    public ResponseEntity<List<AdminPurchaseListItemResponse>> list() {
        return ResponseEntity.ok(service.listAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminPurchaseDetailResponse> detail(
            @PathVariable UUID id, @RequestParam String type) {
        return ResponseEntity.ok(service.detail(id, type));
    }

    @PostMapping("/{purchaseId}/confirm-payment")
    public ResponseEntity<AdminPurchaseDetailResponse> confirmPayment(
            @PathVariable UUID purchaseId,
            @Valid @RequestBody(required = false) ConfirmPaymentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String adminName =
                userDetails.getUser().getName() + " " + userDetails.getUser().getLastName();
        return ResponseEntity.ok(service.confirmPayment(purchaseId, request, adminName));
    }
}
