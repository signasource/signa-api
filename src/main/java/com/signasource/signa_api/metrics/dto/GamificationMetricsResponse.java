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
        ShopSection shop) {

    public record DailyCount(LocalDate day, long count) {}

    public record NameCount(String name, long count) {}

    public record XpSection(long total, List<DailyCount> daily) {}

    public record StreaksSection(
            double averageCurrentStreak, long usersOverSevenDays, long activeStreakers) {}

    public record AchievementsSection(
            long unlocked, List<DailyCount> daily, List<NameCount> topUnlocked) {}

    public record ChallengeClaim(
            String type, long completed, long claimed, double claimRatePercent) {}

    public record GiftsSection(long sent, long claimed) {}

    public record ItemSpend(String title, long purchases, long gemsSpent) {}

    public record ShopSection(long totalGemsSpent, List<ItemSpend> topItems) {}
}
