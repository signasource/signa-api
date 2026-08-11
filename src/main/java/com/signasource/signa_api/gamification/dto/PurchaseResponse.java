package com.signasource.signa_api.gamification.dto;

import java.time.Instant;
import java.util.UUID;

public record PurchaseResponse(
        UUID id,
        ShopItemResponse item,
        int gemsSpent,
        Instant purchasedAt,
        AppliedEffectResponse effect,
        UserInventoryResponse inventory) {}
