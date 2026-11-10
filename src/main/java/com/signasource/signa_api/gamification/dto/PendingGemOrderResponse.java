package com.signasource.signa_api.gamification.dto;

import com.signasource.signa_api.gamification.entity.GemOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A gem order whose payment is in process at Mercado Pago (cash, transfer, review). */
public record PendingGemOrderResponse(
        UUID id, int gems, BigDecimal amount, String currency, Instant createdAt) {

    public static PendingGemOrderResponse from(GemOrder order) {
        return new PendingGemOrderResponse(
                order.getId(),
                order.getGems(),
                order.getAmount(),
                order.getCurrency(),
                order.getCreatedAt());
    }
}
