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
        LocalDate toDate = until.atZone(ZoneOffset.UTC).toLocalDate();

        return new GamificationMetricsResponse(
                xp(fromDate, toDate, from, until),
                streaks(from, until),
                achievements(from, until),
                challenges(from, until),
                new GiftsSection(
                        repository.countGiftsSentBetween(from, until),
                        repository.countGiftsClaimedBetween(from, until)),
                shop(from, until),
                xpDistribution());
    }

    /** Total XP plus the per-active-user average (honest engagement, not inflated by headcount). */
    private XpSection xp(LocalDate fromDate, LocalDate toDate, Instant from, Instant until) {
        long total = repository.sumXpBetween(fromDate, toDate);
        long activeUsers = repository.countActiveUsersBetween(from, until);
        long perActiveUser = activeUsers == 0 ? 0 : Math.round((double) total / activeUsers);
        return new XpSection(
                total,
                perActiveUser,
                activeUsers,
                toDailyCounts(repository.dailyXp(fromDate, toDate)));
    }

    private StreaksSection streaks(Instant from, Instant until) {
        return new StreaksSection(
                round1(repository.averageCurrentStreak()),
                round1(repository.averageStreakActive(from, until)),
                repository.countStreakOver(STREAK_HABIT_DAYS),
                repository.countActiveStreakers(),
                repository.maxStreak());
    }

    private AchievementsSection achievements(Instant from, Instant until) {
        long opportunities = repository.countActiveAchievements() * repository.countEnabledUsers();
        double unlockRate = percent(repository.countAllUnlocked(), opportunities);
        return new AchievementsSection(
                repository.countUnlockedBetween(from, until),
                unlockRate,
                toDailyCounts(repository.dailyUnlocked(from, until)),
                topNames(repository.topUnlockedAchievements()),
                topNames(repository.leastUnlockedAchievements()));
    }

    /** Users bucketed by total XP into experience tiers. */
    private List<NameCount> xpDistribution() {
        List<NameCount> tiers = new ArrayList<>(5);
        tiers.add(new NameCount("Sin XP", repository.countStatsXpBetween(0, 0)));
        tiers.add(new NameCount("1–100", repository.countStatsXpBetween(1, 100)));
        tiers.add(new NameCount("101–500", repository.countStatsXpBetween(101, 500)));
        tiers.add(new NameCount("501–2000", repository.countStatsXpBetween(501, 2000)));
        tiers.add(new NameCount("2000+", repository.countStatsXpOver(2000)));
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
        // Credited = reward inflows (challenges claimed + achievements earned + real-money
        // purchases);
        // gifts move gems between users, so they're economy-neutral and excluded.
        long credited =
                repository.sumChallengeRewardGemsBetween(from, until)
                        + repository.sumAchievementRewardGemsBetween(from, until)
                        + repository.sumGemPurchaseGemsBetween(from, until);
        return new ShopSection(
                spent,
                credited,
                percent(spent, credited),
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
