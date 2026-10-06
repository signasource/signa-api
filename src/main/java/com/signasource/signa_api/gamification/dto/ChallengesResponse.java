package com.signasource.signa_api.gamification.dto;

import java.time.Instant;
import java.util.List;

/**
 * {@code daily} stays empty until every first step has been claimed. {@code resetsAt} is when
 * today's daily challenges roll over (midnight in Argentina).
 */
public record ChallengesResponse(
        List<ChallengeResponse> firstSteps,
        boolean firstStepsDone,
        List<ChallengeResponse> daily,
        Instant resetsAt) {}
