package com.signasource.signa_api.gamification.dto;

import com.signasource.signa_api.gamification.entity.Challenge;
import com.signasource.signa_api.gamification.entity.ChallengeCriteriaType;
import com.signasource.signa_api.gamification.entity.ChallengeRewardType;
import com.signasource.signa_api.gamification.entity.ChallengeType;
import com.signasource.signa_api.gamification.entity.UserChallenge;
import java.util.UUID;

/** A challenge as one user sees it. {@code id} is the user's row: it is what gets claimed. */
public record ChallengeResponse(
        UUID id,
        String code,
        String title,
        String description,
        ChallengeType challengeType,
        ChallengeCriteriaType criteriaType,
        int target,
        int progress,
        boolean completed,
        boolean rewardClaimed,
        ChallengeRewardType rewardType,
        int rewardQuantity,
        Integer rewardDurationMinutes,
        Double rewardMultiplierValue) {

    public static ChallengeResponse from(UserChallenge userChallenge) {
        Challenge challenge = userChallenge.getChallenge();
        return new ChallengeResponse(
                userChallenge.getId(),
                challenge.getCode(),
                challenge.getTitle(),
                challenge.getDescription(),
                challenge.getChallengeType(),
                challenge.getCriteriaType(),
                challenge.getCriteriaValue(),
                userChallenge.getCurrentProgress(),
                userChallenge.isCompleted(),
                userChallenge.getRewardClaimedAt() != null,
                challenge.getRewardType(),
                challenge.getRewardQuantity(),
                challenge.getRewardDurationMinutes(),
                challenge.getRewardMultiplierValue());
    }
}
