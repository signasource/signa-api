package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.UserAchievement;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAchievementRepository extends JpaRepository<UserAchievement, UUID> {
    List<UserAchievement> findByUser(User user);

    Optional<UserAchievement> findByUserAndAchievementId(User user, UUID achievementId);
}
