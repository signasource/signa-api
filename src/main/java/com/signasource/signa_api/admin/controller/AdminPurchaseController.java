package com.signasource.signa_api.admin.controller;

import com.signasource.signa_api.admin.dto.AdminPurchaseResponse;
import com.signasource.signa_api.admin.dto.ConfirmPaymentRequest;
import com.signasource.signa_api.organizations.dto.PurchaseDetailResponse;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.service.PurchasePaymentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    private final PurchasePaymentService paymentService;

    @GetMapping
    public ResponseEntity<List<AdminPurchaseResponse>> list(
            @RequestParam(required = false) PurchaseStatus status) {
        return ResponseEntity.ok(paymentService.listForAdmin(status));
    }

    @PostMapping("/{purchaseId}/confirm-payment")
    public ResponseEntity<PurchaseDetailResponse> confirmPayment(
            @PathVariable UUID purchaseId,
            @Valid @RequestBody(required = false) ConfirmPaymentRequest request) {
        return ResponseEntity.ok(
                paymentService.confirmPayment(
                        purchaseId, request == null ? null : request.paymentReference()));
    }
}
