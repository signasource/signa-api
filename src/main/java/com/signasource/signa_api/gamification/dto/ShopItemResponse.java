package com.signasource.signa_api.gamification.dto;

import com.signasource.signa_api.gamification.entity.ShopItem;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import java.util.UUID;

public record ShopItemResponse(
        UUID id,
        String code,
        String title,
        String description,
        ShopItemType itemType,
        int priceGems,
        int quantity,
        Integer durationMinutes,
        Double multiplierValue,
        boolean active) {

    public static ShopItemResponse from(ShopItem item) {
        return new ShopItemResponse(
                item.getId(),
                item.getCode(),
                item.getTitle(),
                item.getDescription(),
                item.getItemType(),
                item.getPriceGems(),
                item.getQuantity(),
                item.getDurationMinutes(),
                item.getMultiplierValue(),
                item.isActive());
    }
}
