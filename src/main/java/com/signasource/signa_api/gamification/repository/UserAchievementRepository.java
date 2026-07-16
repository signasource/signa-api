package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.UserAchievement;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAchievementRepository extends JpaRepository<UserAchievement, UUID> {}
