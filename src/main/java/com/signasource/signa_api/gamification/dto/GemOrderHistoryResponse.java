package com.signasource.signa_api.gamification.dto;

import com.signasource.signa_api.gamification.entity.GemOrder;
import com.signasource.signa_api.gamification.entity.GemOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One entry in a user's gem-order purchase history. */
public record GemOrderHistoryResponse(
        UUID id,
        String packName,
        int gems,
        BigDecimal amount,
        String currency,
        GemOrderStatus status,
        Instant createdAt,
        Instant paidAt,
        Instant refundedAt) {

    public static GemOrderHistoryResponse from(GemOrder o) {
        return new GemOrderHistoryResponse(
                o.getId(),
                o.getGemPack().getProductId(),
                o.getGems(),
                o.getAmount(),
                o.getCurrency(),
                o.getStatus(),
                o.getCreatedAt(),
                o.getPaidAt(),
                o.getRefundedAt());
    }
}
