package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.users.entity.User;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserStatsRepository extends JpaRepository<UserStats, UUID> {

    Optional<UserStats> findByUserId(UUID userId);

    Optional<UserStats> findByUser(User user);

    /** The global ranking is public-only: private and disabled accounts never appear in it. */
    @Query(
            "SELECT us FROM UserStats us JOIN FETCH us.user u "
                    + "WHERE u.role = com.signasource.signa_api.users.entity.Role.USER "
                    + "AND u.enabled = true AND u.accountVisibility = com.signasource.signa_api.users.entity.AccountVisibility.PUBLIC "
                    + "ORDER BY us.weeklyXp DESC, u.id ASC")
    List<UserStats> findTopByWeeklyXpDesc(Pageable pageable);

    @Query(
            "SELECT us FROM UserStats us JOIN FETCH us.user WHERE us.user.id IN :ids ORDER BY us.weeklyXp DESC, us.user.id ASC")
    List<UserStats> findByUserIdInOrderByWeeklyXpDesc(@Param("ids") Collection<UUID> ids);

    @Query(
            "SELECT COUNT(us) FROM UserStats us JOIN us.user u "
                    + "WHERE us.weeklyXp > :xp AND u.id != :excludeId "
                    + "AND u.role = com.signasource.signa_api.users.entity.Role.USER "
                    + "AND u.enabled = true AND u.accountVisibility = com.signasource.signa_api.users.entity.AccountVisibility.PUBLIC")
    long countUsersAheadByWeeklyXp(@Param("xp") int xp, @Param("excludeId") UUID excludeId);

    @Query(
            "SELECT COUNT(us) FROM UserStats us JOIN us.user u "
                    + "WHERE u.role = com.signasource.signa_api.users.entity.Role.USER "
                    + "AND u.enabled = true AND u.accountVisibility = com.signasource.signa_api.users.entity.AccountVisibility.PUBLIC")
    long countUsersWithUserRole();

    @Modifying
    @Query(
            value =
                    """
            WITH ranked AS (
                SELECT us.id, ROW_NUMBER() OVER (ORDER BY us.weekly_xp DESC, us.id) AS rn
                FROM user_stats us
                JOIN users u ON us.user_id = u.id
                WHERE u.role = 'USER' AND u.enabled = true AND u.account_visibility = 'PUBLIC'
            )
            UPDATE user_stats SET previous_weekly_rank = ranked.rn
            FROM ranked WHERE user_stats.id = ranked.id
            """,
            nativeQuery = true)
    void computeAndSavePreviousWeeklyRanks();

    @Modifying
    @Query("UPDATE UserStats us SET us.weeklyXp = 0")
    void resetAllWeeklyXp();

    /**
     * Returns all stats rows where the streak is active but the last activity pre-dates {@code
     * date}. Used by the nightly scheduler to burn shields or break streaks for users who missed
     * the previous day.
     */
    @Query(
            "SELECT us FROM UserStats us WHERE us.currentStreak > 0"
                    + " AND us.lastStreakDate IS NOT NULL AND us.lastStreakDate < :date")
    List<UserStats> findAllWithActiveStreakBefore(@Param("date") LocalDate date);
}
