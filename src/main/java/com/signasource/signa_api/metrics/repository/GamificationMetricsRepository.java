package com.signasource.signa_api.metrics.repository;

import com.signasource.signa_api.gamification.entity.UserDailyXp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Read-only aggregates behind the usage dashboard's "Gamificación & engagement" apartado. Grouped
 * queries return {@code Object[]} rows mapped in {@code GamificationMetricsService}; happenings are
 * counted by instant window {@code [from, until)} (XP by calendar day, bounds inclusive), streaks
 * are the current state. Verified against H2 in {@code GamificationMetricsRepositoryTest}.
 */
public interface GamificationMetricsRepository extends Repository<UserDailyXp, UUID> {

    // ── XP (day resolution) ──────────────────────────────────────────────────────────────────────
    @Query(
            "SELECT COALESCE(SUM(x.xpEarned), 0) FROM UserDailyXp x "
                    + "WHERE x.xpDate >= :fromDate AND x.xpDate <= :toDate")
    long sumXpBetween(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    /** Rows of {@code [LocalDate day, long xp]}. */
    @Query(
            "SELECT x.xpDate, COALESCE(SUM(x.xpEarned), 0) FROM UserDailyXp x "
                    + "WHERE x.xpDate >= :fromDate AND x.xpDate <= :toDate GROUP BY x.xpDate")
    List<Object[]> dailyXp(
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    // ── Streaks (current state) ──────────────────────────────────────────────────────────────────
    @Query("SELECT AVG(s.currentStreak) FROM UserStats s WHERE s.currentStreak > 0")
    Double averageCurrentStreak();

    @Query("SELECT COUNT(s) FROM UserStats s WHERE s.currentStreak > :days")
    long countStreakOver(@Param("days") int days);

    @Query("SELECT COUNT(s) FROM UserStats s WHERE s.currentStreak > 0")
    long countActiveStreakers();

    @Query("SELECT COALESCE(MAX(s.longestStreak), 0) FROM UserStats s")
    long maxStreak();

    // ── XP distribution (users bucketed by total XP) + engagement
    // ──────────────────────────────────
    @Query(
            "SELECT COUNT(DISTINCT a.user.id) FROM UserActivity a "
                    + "WHERE a.occurredAt >= :from AND a.occurredAt < :until")
    long countActiveUsersBetween(@Param("from") Instant from, @Param("until") Instant until);

    /** XP each user earned between the two days (inclusive), one row per user who earned any. */
    @Query(
            "SELECT SUM(x.xpEarned) FROM UserDailyXp x "
                    + "WHERE x.xpDate >= :fromDate AND x.xpDate <= :toDate GROUP BY x.user.id")
    List<Long> xpEarnedPerUserBetween(
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);

    @Query("SELECT COUNT(s) FROM UserStats s " + "WHERE s.totalXp >= :lo AND s.totalXp <= :hi")
    long countStatsXpBetween(@Param("lo") long lo, @Param("hi") long hi);

    @Query("SELECT COUNT(s) FROM UserStats s WHERE s.totalXp > :threshold")
    long countStatsXpOver(@Param("threshold") long threshold);

    /** Average current streak among users who were active (any activity) within the window. */
    @Query(
            "SELECT AVG(s.currentStreak) FROM UserStats s WHERE s.currentStreak > 0 AND s.user.id IN "
                    + "(SELECT DISTINCT a.user.id FROM UserActivity a "
                    + "WHERE a.occurredAt >= :from AND a.occurredAt < :until)")
    Double averageStreakActive(@Param("from") Instant from, @Param("until") Instant until);

    // ── Achievements ─────────────────────────────────────────────────────────────────────────────
    @Query(
            "SELECT COUNT(a) FROM UserAchievement a "
                    + "WHERE a.earnedAt >= :from AND a.earnedAt < :until")
    long countUnlockedBetween(@Param("from") Instant from, @Param("until") Instant until);

    /** Rows of {@code [LocalDate day, long count]} of achievements unlocked. */
    @Query(
            "SELECT CAST(a.earnedAt AS LocalDate), COUNT(a) FROM UserAchievement a "
                    + "WHERE a.earnedAt >= :from AND a.earnedAt < :until "
                    + "GROUP BY CAST(a.earnedAt AS LocalDate)")
    List<Object[]> dailyUnlocked(@Param("from") Instant from, @Param("until") Instant until);

    /** Rows of {@code [String title, long count]} ordered by most unlocked first (all-time). */
    @Query(
            "SELECT a.achievement.title, COUNT(a) FROM UserAchievement a "
                    + "GROUP BY a.achievement.title ORDER BY COUNT(a) DESC")
    List<Object[]> topUnlockedAchievements();

    /**
     * Rows of {@code [String title, long count]} for every active achievement, least unlocked first
     * — a LEFT JOIN so achievements nobody has (count 0) are included.
     */
    @Query(
            "SELECT a.title, COUNT(ua) FROM Achievement a "
                    + "LEFT JOIN UserAchievement ua ON ua.achievement = a "
                    + "WHERE a.active = true "
                    + "GROUP BY a.id, a.title ORDER BY COUNT(ua) ASC")
    List<Object[]> leastUnlockedAchievements();

    // Only active achievements, so numerator and denominator (active × users) share one universe.
    @Query("SELECT COUNT(ua) FROM UserAchievement ua WHERE ua.achievement.active = true")
    long countAllUnlocked();

    @Query("SELECT COUNT(a) FROM Achievement a WHERE a.active = true")
    long countActiveAchievements();

    @Query(
            "SELECT COUNT(u) FROM User u WHERE u.enabled = true AND (u.role IS NULL OR u.role <> com.signasource.signa_api.users.entity.Role.ADMIN)")
    long countEnabledUsers();

    @Query(
            "SELECT COALESCE(SUM(ua.achievement.rewardGems), 0) FROM UserAchievement ua "
                    + "WHERE ua.earnedAt >= :from AND ua.earnedAt < :until")
    long sumAchievementRewardGemsBetween(
            @Param("from") Instant from, @Param("until") Instant until);

    /** Gems granted by claimed challenges whose reward is gems, in the window. */
    @Query(
            "SELECT COALESCE(SUM(uc.challenge.rewardQuantity), 0) FROM UserChallenge uc "
                    + "WHERE uc.rewardClaimedAt >= :from AND uc.rewardClaimedAt < :until "
                    + "AND uc.challenge.rewardType = "
                    + "com.signasource.signa_api.gamification.entity.ChallengeRewardType.GEMS")
    long sumChallengeRewardGemsBetween(@Param("from") Instant from, @Param("until") Instant until);

    /** Gems granted by paid real-money gem orders in the window. */
    @Query(
            "SELECT COALESCE(SUM(g.gems), 0) FROM GemOrder g "
                    + "WHERE g.paidAt >= :from AND g.paidAt < :until "
                    + "AND g.status = com.signasource.signa_api.gamification.entity.GemOrderStatus.PAID ")
    long sumGemPurchaseGemsBetween(@Param("from") Instant from, @Param("until") Instant until);

    // ── Challenges ───────────────────────────────────────────────────────────────────────────────
    /**
     * Rows of {@code [ChallengeType type, long completed, long claimed]} completed in the window.
     */
    @Query(
            "SELECT uc.challenge.challengeType, COUNT(uc), "
                    + "SUM(CASE WHEN uc.rewardClaimedAt IS NOT NULL THEN 1L ELSE 0L END) "
                    + "FROM UserChallenge uc "
                    + "WHERE uc.completed = true AND uc.completedAt >= :from AND uc.completedAt < :until "
                    + "GROUP BY uc.challenge.challengeType")
    List<Object[]> challengeClaims(@Param("from") Instant from, @Param("until") Instant until);

    // ── Gifts ────────────────────────────────────────────────────────────────────────────────────
    @Query("SELECT COUNT(g) FROM Gift g WHERE g.sentAt >= :from AND g.sentAt < :until")
    long countGiftsSentBetween(@Param("from") Instant from, @Param("until") Instant until);

    @Query("SELECT COUNT(g) FROM Gift g WHERE g.claimedAt >= :from AND g.claimedAt < :until")
    long countGiftsClaimedBetween(@Param("from") Instant from, @Param("until") Instant until);

    // ── Shop economy ─────────────────────────────────────────────────────────────────────────────
    @Query(
            "SELECT COALESCE(SUM(p.gemsSpent), 0) FROM Purchase p "
                    + "WHERE p.purchasedAt >= :from AND p.purchasedAt < :until")
    long sumGemsSpentBetween(@Param("from") Instant from, @Param("until") Instant until);

    /** Gems currently held by all users (sum of every balance). */
    @Query("SELECT COALESCE(SUM(s.gems), 0) FROM UserStats s")
    long sumGemsInInventory();

    /**
     * Rows of {@code [String title, long purchases, long gemsSpent]} ordered by most bought first.
     */
    @Query(
            "SELECT p.shopItem.title, COUNT(p), COALESCE(SUM(p.gemsSpent), 0) FROM Purchase p "
                    + "WHERE p.purchasedAt >= :from AND p.purchasedAt < :until "
                    + "GROUP BY p.shopItem.title ORDER BY COUNT(p) DESC")
    List<Object[]> topShopItems(@Param("from") Instant from, @Param("until") Instant until);

    /** Rows of {@code [LocalDate day, long gemsSpent]} spent in the shop. */
    @Query(
            "SELECT CAST(p.purchasedAt AS LocalDate), COALESCE(SUM(p.gemsSpent), 0) FROM Purchase p "
                    + "WHERE p.purchasedAt >= :from AND p.purchasedAt < :until "
                    + "GROUP BY CAST(p.purchasedAt AS LocalDate)")
    List<Object[]> dailyGemsSpent(@Param("from") Instant from, @Param("until") Instant until);
}
