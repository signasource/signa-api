package com.signasource.signa_api.gamification.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.gamification.dto.PurchaseRequest;
import com.signasource.signa_api.gamification.dto.PurchaseResponse;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.service.PurchaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PurchaseController {

    private final PurchaseService purchaseService;

    @PostMapping("/store/purchases")
    public ResponseEntity<PurchaseResponse> purchaseForSelf(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody PurchaseRequest request) {
        PurchaseResponse response =
                purchaseService.purchaseForSelf(userDetails.getUser(), request.shopItemId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/purchases/{itemType}/claim")
    public ResponseEntity<PurchaseResponse> claim(
            @PathVariable ShopItemType itemType,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(purchaseService.claim(userDetails.getUser(), itemType));
    }
}
