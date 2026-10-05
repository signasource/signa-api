package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.Challenge;
import com.signasource.signa_api.gamification.entity.ChallengeCriteriaType;
import com.signasource.signa_api.gamification.entity.ChallengeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {

    List<Challenge> findByActiveTrueAndChallengeTypeIn(Collection<ChallengeType> types);

    List<Challenge> findByActiveTrueAndCriteriaType(ChallengeCriteriaType criteriaType);
}
