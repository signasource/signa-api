package com.signasource.signa_api.gamification.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.gamification.dto.CreateGemOrderRequest;
import com.signasource.signa_api.gamification.dto.GemCheckoutResponse;
import com.signasource.signa_api.gamification.dto.GemOrderHistoryResponse;
import com.signasource.signa_api.gamification.dto.GemOrderResponse;
import com.signasource.signa_api.gamification.dto.GemPackResponse;
import com.signasource.signa_api.gamification.dto.PendingGemOrderResponse;
import com.signasource.signa_api.gamification.repository.GemOrderRepository;
import com.signasource.signa_api.gamification.service.GemOrderService;
import com.signasource.signa_api.organizations.dto.CreateRefundRequestRequest;
import com.signasource.signa_api.organizations.dto.RefundEligibilityResponse;
import com.signasource.signa_api.organizations.dto.UserRefundRequestResponse;
import com.signasource.signa_api.organizations.service.UserRefundRequestService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GemOrderController {

    private final GemOrderService gemOrderService;
    private final GemOrderRepository gemOrderRepository;
    private final UserRefundRequestService refundRequestService;

    @GetMapping("/store/gem-packs")
    public ResponseEntity<List<GemPackResponse>> getPacks() {
        return ResponseEntity.ok(gemOrderService.getPacks());
    }

    @PostMapping("/store/gem-orders")
    public ResponseEntity<GemCheckoutResponse> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateGemOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(gemOrderService.startCheckout(userDetails.getUser(), request.packId()));
    }

    /** Orders whose payment is in process at Mercado Pago; empty when there is nothing pending. */
    @GetMapping("/store/gem-orders/pending")
    public ResponseEntity<List<PendingGemOrderResponse>> pending(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(gemOrderService.pending(userDetails.getUser()));
    }

    @PostMapping("/store/gem-orders/{orderId}/sync")
    public ResponseEntity<GemOrderResponse> sync(
            @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable UUID orderId) {
        return ResponseEntity.ok(gemOrderService.sync(userDetails.getUser(), orderId));
    }

    @GetMapping("/store/gem-orders")
    public ResponseEntity<List<GemOrderHistoryResponse>> history(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<GemOrderHistoryResponse> result =
                gemOrderRepository
                        .findByUserIdOrderByCreatedAtDesc(userDetails.getUser().getId())
                        .stream()
                        .map(GemOrderHistoryResponse::from)
                        .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/store/gem-orders/{orderId}/refund-eligibility")
    public ResponseEntity<RefundEligibilityResponse> eligibility(
            @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable UUID orderId) {
        return ResponseEntity.ok(
                refundRequestService.checkEligibilityGems(orderId, userDetails.getUser()));
    }

    @PostMapping("/store/gem-orders/{orderId}/refund-request")
    public ResponseEntity<UserRefundRequestResponse> requestRefund(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID orderId,
            @Valid @RequestBody CreateRefundRequestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        refundRequestService.createForGemOrder(
                                orderId, userDetails.getUser(), request));
    }
}
