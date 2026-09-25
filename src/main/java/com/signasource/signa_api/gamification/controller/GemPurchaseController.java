package com.signasource.signa_api.gamification.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.gamification.dto.GemPackResponse;
import com.signasource.signa_api.gamification.dto.GemPurchaseResponse;
import com.signasource.signa_api.gamification.dto.RedeemGemPurchaseRequest;
import com.signasource.signa_api.gamification.service.GemPurchaseService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GemPurchaseController {

    private final GemPurchaseService gemPurchaseService;

    @GetMapping("/store/gem-packs")
    public ResponseEntity<List<GemPackResponse>> getPacks() {
        return ResponseEntity.ok(gemPurchaseService.getPacks());
    }

    @PostMapping("/store/gem-purchases")
    public ResponseEntity<GemPurchaseResponse> redeem(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RedeemGemPurchaseRequest request) {
        GemPurchaseResponse response =
                gemPurchaseService.redeem(
                        userDetails.getUser(), request.productId(), request.purchaseToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
