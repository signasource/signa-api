package com.signasource.signa_api.organizations.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.organizations.dto.CheckoutResponse;
import com.signasource.signa_api.organizations.dto.CreatePurchaseRequest;
import com.signasource.signa_api.organizations.dto.PaymentSyncResponse;
import com.signasource.signa_api.organizations.dto.PurchaseCatalogResponse;
import com.signasource.signa_api.organizations.dto.PurchaseDetailResponse;
import com.signasource.signa_api.organizations.dto.PurchaseQuoteResponse;
import com.signasource.signa_api.organizations.dto.PurchaseResponse;
import com.signasource.signa_api.organizations.dto.SyncPaymentRequest;
import com.signasource.signa_api.organizations.dto.UpdateSeatEmailRequest;
import com.signasource.signa_api.organizations.dto.WithdrawPurchaseRequest;
import com.signasource.signa_api.organizations.service.CoursePurchaseService;
import com.signasource.signa_api.organizations.service.MercadoPagoCheckoutService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/organizations")
@RequiredArgsConstructor
public class OrganizationPurchaseController {

    private final CoursePurchaseService purchaseService;
    private final MercadoPagoCheckoutService checkoutService;

    @GetMapping("/catalog")
    public ResponseEntity<PurchaseCatalogResponse> getCatalog() {
        return ResponseEntity.ok(purchaseService.catalog());
    }

    @GetMapping("/catalog/quote")
    public ResponseEntity<PurchaseQuoteResponse> quote(
            @RequestParam UUID courseId, @RequestParam int seats, @RequestParam int durationDays) {
        return ResponseEntity.ok(purchaseService.quote(courseId, seats, durationDays));
    }

    @GetMapping("/{organizationId}/purchases")
    public ResponseEntity<List<PurchaseResponse>> listPurchases(
            @PathVariable UUID organizationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(purchaseService.list(userDetails.getUser(), organizationId));
    }

    @PostMapping("/{organizationId}/purchases")
    public ResponseEntity<PurchaseDetailResponse> createPurchase(
            @PathVariable UUID organizationId,
            @Valid @RequestBody CreatePurchaseRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(purchaseService.create(userDetails.getUser(), organizationId, request));
    }

    @GetMapping("/{organizationId}/purchases/{purchaseId}")
    public ResponseEntity<PurchaseDetailResponse> getPurchase(
            @PathVariable UUID organizationId,
            @PathVariable UUID purchaseId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                purchaseService.get(userDetails.getUser(), organizationId, purchaseId));
    }

    @PostMapping("/{organizationId}/purchases/{purchaseId}/cancel")
    public ResponseEntity<PurchaseDetailResponse> cancelPurchase(
            @PathVariable UUID organizationId,
            @PathVariable UUID purchaseId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                checkoutService.cancel(userDetails.getUser(), organizationId, purchaseId));
    }

    @PostMapping("/{organizationId}/purchases/{purchaseId}/checkout")
    public ResponseEntity<CheckoutResponse> startCheckout(
            @PathVariable UUID organizationId,
            @PathVariable UUID purchaseId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                checkoutService.startCheckout(userDetails.getUser(), organizationId, purchaseId));
    }

    /** The organization cancels a paid purchase within the withdrawal period, for a full refund. */
    @PostMapping("/{organizationId}/purchases/{purchaseId}/refund")
    public ResponseEntity<PurchaseDetailResponse> withdrawPurchase(
            @PathVariable UUID organizationId,
            @PathVariable UUID purchaseId,
            @Valid @RequestBody(required = false) WithdrawPurchaseRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                checkoutService.withdraw(
                        userDetails.getUser(),
                        organizationId,
                        purchaseId,
                        request == null ? null : request.reason()));
    }

    @PostMapping("/{organizationId}/purchases/{purchaseId}/payments/sync")
    public ResponseEntity<PaymentSyncResponse> syncPayment(
            @PathVariable UUID organizationId,
            @PathVariable UUID purchaseId,
            @Valid @RequestBody SyncPaymentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                checkoutService.syncPayment(
                        userDetails.getUser(), organizationId, purchaseId, request.paymentId()));
    }

    @PatchMapping("/{organizationId}/purchases/{purchaseId}/seats/{seatId}")
    public ResponseEntity<PurchaseDetailResponse> updateSeatEmail(
            @PathVariable UUID organizationId,
            @PathVariable UUID purchaseId,
            @PathVariable UUID seatId,
            @Valid @RequestBody UpdateSeatEmailRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                purchaseService.updateSeatEmail(
                        userDetails.getUser(), organizationId, purchaseId, seatId, request));
    }

    @PostMapping("/{organizationId}/purchases/{purchaseId}/seats/{seatId}/reminders")
    public ResponseEntity<PurchaseDetailResponse> remindSeat(
            @PathVariable UUID organizationId,
            @PathVariable UUID purchaseId,
            @PathVariable UUID seatId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(
                purchaseService.remindSeat(
                        userDetails.getUser(), organizationId, purchaseId, seatId));
    }
}
