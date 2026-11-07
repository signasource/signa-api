package com.signasource.signa_api.gamification.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.gamification.dto.CreateGemOrderRequest;
import com.signasource.signa_api.gamification.dto.GemCheckoutResponse;
import com.signasource.signa_api.gamification.dto.GemOrderResponse;
import com.signasource.signa_api.gamification.dto.GemPackResponse;
import com.signasource.signa_api.gamification.service.GemOrderService;
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

    @PostMapping("/store/gem-orders/{orderId}/sync")
    public ResponseEntity<GemOrderResponse> sync(
            @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable UUID orderId) {
        return ResponseEntity.ok(gemOrderService.sync(userDetails.getUser(), orderId));
    }
}
