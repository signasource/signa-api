package com.signasource.signa_api.gamification.dto;

import com.signasource.signa_api.gamification.entity.LivesMode;
import com.signasource.signa_api.gamification.entity.UserStats;
import java.time.Instant;

public record UserStatsResponse(
        long totalXp,
        int weeklyXp,
        Integer weeklyRank,
        int currentStreak,
        int longestStreak,
        int gems,
        int streakShields,
        int learnedSignsCount,
        int currentLives,
        LivesMode livesMode,
        Instant nextLifeAt,
        boolean hasActiveXpMultiplier,
        double xpMultiplier,
        Instant xpMultiplierExpiresAt,
        Instant unlimitedLivesExpiresAt) {

    /** {@code weeklyRank} is null for private accounts, which are not in the global ranking. */
    public static UserStatsResponse from(UserStats stats, Integer weeklyRank) {
        return new UserStatsResponse(
                stats.getTotalXp(),
                stats.getWeeklyXp(),
                weeklyRank,
                stats.getCurrentStreak(),
                stats.getLongestStreak(),
                stats.getGems(),
                stats.getStreakShields(),
                stats.getLearnedSignsCount(),
                stats.lifeStateAt(java.time.Instant.now()).lives(),
                stats.getEffectiveLivesMode(),
                stats.lifeStateAt(java.time.Instant.now()).nextLifeAt(),
                stats.hasActiveXpMultiplier(),
                stats.getEffectiveXpMultiplier(),
                stats.getXpMultiplierExpiresAt(),
                stats.getUnlimitedLivesExpiresAt());
    }
}
