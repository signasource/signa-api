package com.signasource.signa_api.gamification.dto;

import com.signasource.signa_api.gamification.entity.Purchase;
import java.time.Instant;
import java.util.UUID;

public record PurchaseResponse(
        UUID purchaseId, ShopItemResponse item, int gemsSpent, Instant purchasedAt) {

    public static PurchaseResponse from(Purchase purchase) {
        return new PurchaseResponse(
                purchase.getId(),
                ShopItemResponse.from(purchase.getShopItem()),
                purchase.getGemsSpent(),
                purchase.getPurchasedAt());
    }
}
