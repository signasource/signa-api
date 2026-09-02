package com.signasource.signa_api.gamification.dto;

import com.signasource.signa_api.gamification.entity.Purchase;
import com.signasource.signa_api.gamification.entity.PurchaseStatus;
import com.signasource.signa_api.gamification.entity.UserStats;
import java.time.Instant;
import java.util.UUID;

public record PurchaseResponse(
        UUID id,
        ShopItemResponse item,
        int gemsSpent,
        Instant purchasedAt,
        PurchaseStatus status,
        Instant activatedAt,
        AppliedEffectResponse effect,
        UserInventoryResponse inventory) {

    public static PurchaseResponse from(Purchase purchase, UserStats stats) {
        return new PurchaseResponse(
                purchase.getId(),
                ShopItemResponse.from(purchase.getShopItem()),
                purchase.getGemsSpent(),
                purchase.getPurchasedAt(),
                purchase.getStatus(),
                purchase.getActivatedAt(),
                null,
                UserInventoryResponse.from(stats));
    }
}
