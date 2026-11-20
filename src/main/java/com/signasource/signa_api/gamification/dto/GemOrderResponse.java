package com.signasource.signa_api.gamification.dto;

import com.signasource.signa_api.gamification.entity.GemOrder;
import com.signasource.signa_api.gamification.entity.GemOrderStatus;
import com.signasource.signa_api.gamification.entity.UserStats;
import java.util.UUID;

/** State of a gem order. {@code inventory} is the buyer's wallet right now. */
public record GemOrderResponse(
        UUID id, GemOrderStatus status, int gems, UserInventoryResponse inventory) {

    public static GemOrderResponse from(GemOrder order, UserStats stats) {
        return new GemOrderResponse(
                order.getId(),
                order.getStatus(),
                order.getGems(),
                UserInventoryResponse.from(stats));
    }
}
