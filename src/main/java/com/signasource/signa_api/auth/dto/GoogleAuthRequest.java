package com.signasource.signa_api.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * {@code dailyGoalMinutes} is the goal picked in the onboarding survey. It only applies when the
 * call creates the account; an existing account keeps its own goal.
 */
public record GoogleAuthRequest(
        @NotBlank String idToken, @Min(1) @Max(1440) Integer dailyGoalMinutes) {}
