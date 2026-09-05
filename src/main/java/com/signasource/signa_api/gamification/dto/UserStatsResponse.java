package com.signasource.signa_api.gamification.dto;

import com.signasource.signa_api.gamification.entity.LivesMode;
import com.signasource.signa_api.gamification.entity.UserStats;
import java.time.Instant;

public record UserStatsResponse(
        long totalXp,
        int weeklyXp,
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

    public static UserStatsResponse from(UserStats stats) {
        return new UserStatsResponse(
                stats.getTotalXp(),
                stats.getWeeklyXp(),
                stats.getCurrentStreak(),
                stats.getLongestStreak(),
                stats.getGems(),
                stats.getStreakShields(),
                stats.getLearnedSignsCount(),
                stats.getCurrentLives() != null ? stats.getCurrentLives() : UserStats.MAX_LIVES,
                stats.getLivesMode(),
                stats.getNextLifeAt(),
                stats.hasActiveXpMultiplier(),
                stats.getEffectiveXpMultiplier(),
                stats.getXpMultiplierExpiresAt(),
                stats.getUnlimitedLivesExpiresAt());
    }
}
