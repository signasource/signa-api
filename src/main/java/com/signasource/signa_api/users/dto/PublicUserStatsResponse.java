package com.signasource.signa_api.users.dto;

import com.signasource.signa_api.gamification.entity.UserStats;

/** Progress only. Gems, lives and boosters stay private. */
public record PublicUserStatsResponse(
        int currentStreak, int longestStreak, long totalXp, int weeklyXp, int learnedSignsCount) {

    public static final PublicUserStatsResponse EMPTY = new PublicUserStatsResponse(0, 0, 0L, 0, 0);

    public static PublicUserStatsResponse from(UserStats stats) {
        if (stats == null) {
            return EMPTY;
        }
        return new PublicUserStatsResponse(
                stats.getCurrentStreak(),
                stats.getLongestStreak(),
                stats.getTotalXp(),
                stats.getWeeklyXp(),
                stats.getLearnedSignsCount());
    }
}
