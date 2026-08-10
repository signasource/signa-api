package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.Achievement;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AchievementRepository extends JpaRepository<Achievement, UUID> {

    @Query(
            "SELECT a, ua FROM Achievement a "
                    + "LEFT JOIN UserAchievement ua ON ua.achievement = a AND ua.user = :user "
                    + "ORDER BY a.title ASC")
    List<Object[]> findAllWithUserAchievement(@Param("user") User user);

    @Query(
            "SELECT a, ua FROM Achievement a "
                    + "LEFT JOIN UserAchievement ua ON ua.achievement = a AND ua.user = :user "
                    + "WHERE a.id = :id")
    Optional<Object[]> findByIdWithUserAchievement(@Param("id") UUID id, @Param("user") User user);
}
