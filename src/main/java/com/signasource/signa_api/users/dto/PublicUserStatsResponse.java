package com.signasource.signa_api.users.dto;

import com.signasource.signa_api.gamification.entity.UserStats;

/**
 * Progress only. Gems, lives and boosters stay private. {@code weeklyRank} is null when the user is
 * not in the global ranking (private account or no stats).
 */
public record PublicUserStatsResponse(
        int currentStreak,
        int longestStreak,
        long totalXp,
        int weeklyXp,
        Integer weeklyRank,
        int learnedSignsCount) {

    public static final PublicUserStatsResponse EMPTY =
            new PublicUserStatsResponse(0, 0, 0L, 0, null, 0);

    public static PublicUserStatsResponse from(UserStats stats, Integer weeklyRank) {
        if (stats == null) {
            return EMPTY;
        }
        return new PublicUserStatsResponse(
                stats.getCurrentStreak(),
                stats.getLongestStreak(),
                stats.getTotalXp(),
                stats.getWeeklyXp(),
                weeklyRank,
                stats.getLearnedSignsCount());
    }
}
