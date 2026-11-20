package com.signasource.signa_api.gamification.dto;

import com.signasource.signa_api.gamification.entity.GemPack;
import java.math.BigDecimal;
import java.util.UUID;

public record GemPackResponse(
        UUID id, String productId, int gems, BigDecimal price, String currency, int sortOrder) {

    public static GemPackResponse from(GemPack pack) {
        return new GemPackResponse(
                pack.getId(),
                pack.getProductId(),
                pack.getGems(),
                pack.getPrice(),
                pack.getCurrency(),
                pack.getSortOrder());
    }
}
