package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.users.entity.User;
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

    @Query(
            "SELECT us FROM UserStats us JOIN FETCH us.user ORDER BY us.weeklyXp DESC, us.user.id ASC")
    List<UserStats> findTopByWeeklyXpDesc(Pageable pageable);

    @Query(
            "SELECT us FROM UserStats us JOIN FETCH us.user WHERE us.user.id IN :ids ORDER BY us.weeklyXp DESC, us.user.id ASC")
    List<UserStats> findByUserIdInOrderByWeeklyXpDesc(@Param("ids") Collection<UUID> ids);

    @Query(
            "SELECT COUNT(us) FROM UserStats us WHERE us.weeklyXp > :xp AND us.user.id != :excludeId")
    long countUsersAheadByWeeklyXp(@Param("xp") int xp, @Param("excludeId") UUID excludeId);

    @Modifying
    @Query(
            value =
                    """
            WITH ranked AS (
                SELECT id, ROW_NUMBER() OVER (ORDER BY weekly_xp DESC, id) AS rn
                FROM user_stats
            )
            UPDATE user_stats SET previous_weekly_rank = ranked.rn
            FROM ranked WHERE user_stats.id = ranked.id
            """,
            nativeQuery = true)
    void computeAndSavePreviousWeeklyRanks();

    @Modifying
    @Query("UPDATE UserStats us SET us.weeklyXp = 0")
    void resetAllWeeklyXp();
}
