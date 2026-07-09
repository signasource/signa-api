package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.UserStats;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStatsRepository extends JpaRepository<UserStats, UUID> {}
