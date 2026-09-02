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
        boolean xpMultiplierActive,
        Instant unlimitedLivesExpiresAt,
        boolean unlimitedLivesActive,
        int learnedSignsCount) {

    public static UserInventoryResponse from(UserStats stats) {
        return new UserInventoryResponse(
                stats.getGems(),
                stats.getStreakShields(),
                stats.getEffectiveLivesMode(),
                stats.getCurrentLives(),
                stats.getNextLifeAt(),
                stats.getEffectiveXpMultiplier(),
                stats.getXpMultiplierExpiresAt(),
                stats.hasActiveXpMultiplier(),
                stats.getUnlimitedLivesExpiresAt(),
                stats.hasActiveUnlimitedLives(),
                stats.getLearnedSignsCount());
    }
}
