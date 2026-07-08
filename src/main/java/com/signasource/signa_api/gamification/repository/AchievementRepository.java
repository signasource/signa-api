package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.Achievement;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AchievementRepository extends JpaRepository<Achievement, UUID> {

}
