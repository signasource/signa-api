package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.UserStats;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserStatsRepository extends JpaRepository<UserStats, UUID> {

    Optional<UserStats> findByUserId(UUID userId);
}
