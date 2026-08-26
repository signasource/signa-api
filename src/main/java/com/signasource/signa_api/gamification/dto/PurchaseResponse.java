package com.signasource.signa_api.gamification.dto;

import com.signasource.signa_api.gamification.entity.Purchase;
import com.signasource.signa_api.gamification.entity.PurchaseStatus;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.entity.UserStats;
import java.time.Instant;
import java.util.UUID;

public record PurchaseResponse(
        UUID purchaseId,
        String itemCode,
        ShopItemType itemType,
        PurchaseStatus status,
        Instant activatedAt,
        UserInventoryResponse inventory) {

    public static PurchaseResponse from(Purchase purchase, UserStats stats) {
        return new PurchaseResponse(
                purchase.getId(),
                purchase.getShopItem().getCode(),
                purchase.getShopItem().getItemType(),
                purchase.getStatus(),
                purchase.getActivatedAt(),
                UserInventoryResponse.from(stats));
    }
}
