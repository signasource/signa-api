package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.Challenge;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {}
