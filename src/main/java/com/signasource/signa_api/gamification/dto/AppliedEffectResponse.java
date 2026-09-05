package com.signasource.signa_api.gamification.dto;

import com.signasource.signa_api.gamification.entity.ShopItemType;

/**
 * Describes the concrete reward that was applied to a user's stats after a purchase or a gift
 * claim. For a {@code MYSTERY_CHEST} item, {@code type} is the resolved reward, not the chest
 * itself, so the frontend can reveal what the user actually won.
 */
public record AppliedEffectResponse(
        ShopItemType type,
        Integer gemsGranted,
        Integer livesGranted,
        Integer streakShieldsGranted,
        Double xpMultiplierValue,
        Integer durationMinutes) {}
