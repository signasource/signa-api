package com.signasource.signa_api.gamification.dto;

import com.signasource.signa_api.gamification.entity.GemPurchase;
import com.signasource.signa_api.gamification.entity.UserStats;
import java.time.Instant;
import java.util.UUID;

/**
 * @param alreadyGranted {@code true} when this token had been redeemed before and no gems were
 *     credited by this call (the app can still safely consume the purchase)
 */
public record GemPurchaseResponse(
        UUID id,
        String productId,
        int gemsGranted,
        String orderId,
        Instant purchasedAt,
        boolean alreadyGranted,
        UserInventoryResponse inventory) {

    public static GemPurchaseResponse from(
            GemPurchase purchase, UserStats stats, boolean alreadyGranted) {
        return new GemPurchaseResponse(
                purchase.getId(),
                purchase.getProductId(),
                purchase.getGemsGranted(),
                purchase.getOrderId(),
                purchase.getPurchasedAt(),
                alreadyGranted,
                UserInventoryResponse.from(stats));
    }
}
