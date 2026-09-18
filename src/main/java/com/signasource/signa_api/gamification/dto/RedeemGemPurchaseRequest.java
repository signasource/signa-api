package com.signasource.signa_api.gamification.dto;

import jakarta.validation.constraints.NotBlank;

/** What the app sends after Google Play reports a completed gem-pack purchase. */
public record RedeemGemPurchaseRequest(
        @NotBlank String productId, @NotBlank String purchaseToken) {}
