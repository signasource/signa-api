package com.signasource.signa_api.gamification.dto;

import java.time.Instant;
import java.util.List;

/**
 * {@code daily} and {@code weekly} stay empty until every first step has been claimed. {@code
 * resetsAt} is when today's daily challenges roll over (midnight in Argentina); {@code
 * weeklyResetsAt} is when this week's challenges do (Monday 00:00 in Argentina).
 */
public record ChallengesResponse(
        List<ChallengeResponse> firstSteps,
        boolean firstStepsDone,
        List<ChallengeResponse> daily,
        Instant resetsAt,
        List<ChallengeResponse> weekly,
        Instant weeklyResetsAt) {}
