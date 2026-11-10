package com.signasource.signa_api.metrics.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Global gamification & engagement snapshot for the usage dashboard's "Gamificación" apartado:
 * daily XP, streak distribution, achievements unlocked, challenge claim rates by type, gifts and
 * the shop economy. Happenings (XP, unlocks, completions, gifts, purchases) are counted inside the
 * window; streaks are the current state (always "now"), matching the rest of the dashboard. XP is
 * day-resolution (`UserDailyXp.xpDate`), so its window is by calendar day, bounds inclusive.
 */
public record GamificationMetricsResponse(
        XpSection xp,
        StreaksSection streaks,
        AchievementsSection achievements,
        List<ChallengeClaim> challenges,
        GiftsSection gifts,
        ShopSection shop,
        /** Users bucketed by total XP (experience tiers) — the distribution, not just the total. */
        List<NameCount> xpDistribution) {

    public record DailyCount(LocalDate day, long count) {}

    public record NameCount(String name, long count) {}

    /**
     * XP over the window: the total (grows just by having more users), the active users it was
     * earned by and the per-active-user average — the honest engagement signal. {@code
     * previousPerActiveUser} is the same metric for the previous equal-length window (null for the
     * whole-history range where there is no prior window).
     */
    public record XpSection(
            long total,
            long perActiveUser,
            long activeUsers,
            List<DailyCount> daily,
            Long previousPerActiveUser) {}

    public record StreaksSection(
            double averageCurrentStreak,
            double averageStreakActive,
            long usersOverSevenDays,
            long activeStreakers,
            /** Longest streak any user has reached (longestStreak across UserStats). */
            long maxStreak) {}

    /**
     * Achievements unlocked in the window plus the all-time unlock rate (unlocked pairs / active
     * achievements × enabled users) and the most/least unlocked achievements (least includes those
     * with zero unlocks). {@code previousUnlocked} is the same count for the previous equal-length
     * window (null for the whole-history range where there is no prior window).
     */
    public record AchievementsSection(
            long unlocked,
            double unlockRatePercent,
            List<DailyCount> daily,
            List<NameCount> topUnlocked,
            List<NameCount> leastUnlocked,
            Long previousUnlocked) {}

    public record ChallengeClaim(
            String type, long completed, long claimed, double claimRatePercent) {}

    public record GiftsSection(long sent, long claimed) {}

    public record ItemSpend(String title, long purchases, long gemsSpent) {}

    /**
     * Shop economy over the window: gems spent in the window, the gems users currently hold in
     * their inventory (all users' balances) and the redemption rate (spent / inventory).
     */
    public record ShopSection(
            long totalGemsSpent,
            long gemsInInventory,
            double redemptionRatePercent,
            List<ItemSpend> topItems,
            List<DailyCount> dailySpent) {}
}
