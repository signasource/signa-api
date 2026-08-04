package com.signasource.signa_api.gamification.dto;

import com.signasource.signa_api.gamification.entity.LivesMode;
import com.signasource.signa_api.gamification.entity.UserStats;
import java.time.Instant;

public record UserInventoryResponse(
        int gems,
        int streakShields,
        LivesMode livesMode,
        Integer currentLives,
        Instant nextLifeAt,
        double effectiveXpMultiplier,
        Instant xpMultiplierExpiresAt,
        boolean xpMultiplierActive) {

    public static UserInventoryResponse from(UserStats stats) {
        return new UserInventoryResponse(
                stats.getGems(),
                stats.getStreakShields(),
                stats.getLivesMode(),
                stats.getCurrentLives(),
                stats.getNextLifeAt(),
                stats.getEffectiveXpMultiplier(),
                stats.getXpMultiplierExpiresAt(),
                stats.hasActiveXpMultiplier());
    }
}