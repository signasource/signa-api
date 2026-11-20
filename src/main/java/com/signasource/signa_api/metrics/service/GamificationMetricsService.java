package com.signasource.signa_api.metrics.service;

import com.signasource.signa_api.metrics.dto.GamificationMetricsResponse;
import com.signasource.signa_api.metrics.dto.GamificationMetricsResponse.AchievementsSection;
import com.signasource.signa_api.metrics.dto.GamificationMetricsResponse.ChallengeClaim;
import com.signasource.signa_api.metrics.dto.GamificationMetricsResponse.DailyCount;
import com.signasource.signa_api.metrics.dto.GamificationMetricsResponse.GiftsSection;
import com.signasource.signa_api.metrics.dto.GamificationMetricsResponse.ItemSpend;
import com.signasource.signa_api.metrics.dto.GamificationMetricsResponse.NameCount;
import com.signasource.signa_api.metrics.dto.GamificationMetricsResponse.ShopSection;
import com.signasource.signa_api.metrics.dto.GamificationMetricsResponse.StreaksSection;
import com.signasource.signa_api.metrics.dto.GamificationMetricsResponse.XpSection;
import com.signasource.signa_api.metrics.repository.GamificationMetricsRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the global gamification snapshot by aggregating Postgres over an instant window. */
@Service
@RequiredArgsConstructor
public class GamificationMetricsService {

    private static final int STREAK_HABIT_DAYS = 7;
    private static final int TOP_LIMIT = 10;

    private final GamificationMetricsRepository repository;

    @Transactional(readOnly = true)
    public GamificationMetricsResponse getGamificationMetrics(MetricsRange range) {
        Instant from = range.start();
        Instant until = range.end();
        LocalDate fromDate = from.atZone(ZoneOffset.UTC).toLocalDate();
        // `until` is exclusive: a window ending at midnight must not pull in the next day's XP.
        LocalDate toDate = until.minusNanos(1).atZone(ZoneOffset.UTC).toLocalDate();

        return new GamificationMetricsResponse(
                xp(fromDate, toDate, from, until, range),
                streaks(from, until),
                achievements(from, until, range),
                challenges(from, until),
                new GiftsSection(
                        repository.countGiftsSentBetween(from, until),
                        repository.countGiftsClaimedBetween(from, until)),
                shop(from, until),
                xpDistribution(fromDate, toDate, repository.countActiveUsersBetween(from, until)));
    }

    /** Total XP plus the per-active-user average (honest engagement, not inflated by headcount). */
    private XpSection xp(
            LocalDate fromDate, LocalDate toDate, Instant from, Instant until, MetricsRange range) {
        long total = repository.sumXpBetween(fromDate, toDate);
        long activeUsers = repository.countActiveUsersBetween(from, until);
        long perActiveUser = activeUsers == 0 ? 0 : Math.round((double) total / activeUsers);

        Long previousPerActiveUser = null;
        if (!range.isOpen()) {
            Duration d = Duration.between(from, until);
            Instant prevFrom = from.minus(d);
            LocalDate prevFromDate = prevFrom.atZone(ZoneOffset.UTC).toLocalDate();
            // Previous window ends the day before the current one starts (no shared day).
            long prevTotal = repository.sumXpBetween(prevFromDate, fromDate.minusDays(1));
            long prevActiveUsers = repository.countActiveUsersBetween(prevFrom, from);
            previousPerActiveUser =
                    prevActiveUsers == 0 ? null : Math.round((double) prevTotal / prevActiveUsers);
        }

        return new XpSection(
                total,
                perActiveUser,
                activeUsers,
                toDailyCounts(repository.dailyXp(fromDate, toDate)),
                previousPerActiveUser);
    }

    private StreaksSection streaks(Instant from, Instant until) {
        return new StreaksSection(
                round1(repository.averageCurrentStreak()),
                round1(repository.averageStreakActive(from, until)),
                repository.countStreakOver(STREAK_HABIT_DAYS),
                repository.countActiveStreakers(),
                repository.maxStreak());
    }

    private AchievementsSection achievements(Instant from, Instant until, MetricsRange range) {
        long eligibleUsers = repository.countEnabledUsers();
        long opportunities = repository.countActiveAchievements() * eligibleUsers;
        double unlockRate = percent(repository.countAllUnlocked(), opportunities);

        Long previousUnlocked = null;
        if (!range.isOpen()) {
            Duration d = Duration.between(from, until);
            Instant prevFrom = from.minus(d);
            previousUnlocked = repository.countUnlockedBetween(prevFrom, from);
        }

        return new AchievementsSection(
                repository.countUnlockedBetween(from, until),
                unlockRate,
                toDailyCounts(repository.dailyUnlocked(from, until)),
                topNames(repository.topUnlockedAchievements()),
                topNames(repository.leastUnlockedAchievements()),
                previousUnlocked,
                eligibleUsers);
    }

    /** Upper bounds (exclusive) of the XP-earned tiers; the last tier is open-ended. */
    static final long[] XP_TIER_BOUNDS = {100, 300, 600, 1000, 2000};

    static final List<String> XP_TIER_LABELS =
            List.of("0–100", "100–300", "300–600", "600–1k", "1k–2k", "2k+");

    /**
     * Active users bucketed by the XP they earned <em>in the window</em> (a histogram: the tiers
     * are ordered). An active user who earned no XP falls in the first tier.
     */
    private List<NameCount> xpDistribution(LocalDate fromDate, LocalDate toDate, long activeUsers) {
        long[] counts = new long[XP_TIER_LABELS.size()];
        List<Long> perUser = repository.xpEarnedPerUserBetween(fromDate, toDate);
        for (Long xp : perUser) {
            long value = xp == null ? 0 : xp;
            int tier = 0;
            while (tier < XP_TIER_BOUNDS.length && value >= XP_TIER_BOUNDS[tier]) {
                tier++;
            }
            counts[tier]++;
        }
        counts[0] += Math.max(0, activeUsers - perUser.size());
        List<NameCount> tiers = new ArrayList<>(counts.length);
        for (int i = 0; i < counts.length; i++) {
            tiers.add(new NameCount(XP_TIER_LABELS.get(i), counts[i]));
        }
        return tiers;
    }

    private static double round1(Double value) {
        return value == null ? 0.0 : Math.round(value * 10.0) / 10.0;
    }

    private List<ChallengeClaim> challenges(Instant from, Instant until) {
        List<ChallengeClaim> claims = new ArrayList<>();
        for (Object[] row : repository.challengeClaims(from, until)) {
            long completed = toLong(row[1]);
            long claimed = toLong(row[2]);
            claims.add(
                    new ChallengeClaim(
                            String.valueOf(row[0]),
                            completed,
                            claimed,
                            percent(claimed, completed)));
        }
        return claims;
    }

    private ShopSection shop(Instant from, Instant until) {
        List<ItemSpend> items = new ArrayList<>();
        for (Object[] row : repository.topShopItems(from, until)) {
            if (items.size() >= TOP_LIMIT) {
                break;
            }
            items.add(new ItemSpend(String.valueOf(row[0]), toLong(row[1]), toLong(row[2])));
        }
        long spent = repository.sumGemsSpentBetween(from, until);
        // Denominator = gems users hold right now (sum of every balance), not period inflows.
        long inventory = repository.sumGemsInInventory();
        return new ShopSection(
                spent,
                inventory,
                percent(spent, inventory),
                items,
                toDailyCounts(repository.dailyGemsSpent(from, until)));
    }

    private static List<NameCount> topNames(List<Object[]> rows) {
        List<NameCount> names = new ArrayList<>();
        for (Object[] row : rows) {
            if (names.size() >= TOP_LIMIT) {
                break;
            }
            names.add(new NameCount(String.valueOf(row[0]), toLong(row[1])));
        }
        return names;
    }

    private static List<DailyCount> toDailyCounts(List<Object[]> rows) {
        List<DailyCount> series = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            series.add(new DailyCount((LocalDate) row[0], toLong(row[1])));
        }
        series.sort(Comparator.comparing(DailyCount::day));
        return series;
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static double percent(long part, long whole) {
        return whole == 0 ? 0.0 : Math.round((part * 1000.0) / whole) / 10.0;
    }
}
