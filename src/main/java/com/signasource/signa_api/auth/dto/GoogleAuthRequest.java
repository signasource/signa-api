package com.signasource.signa_api.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record GoogleAuthRequest(
        @NotBlank String idToken, @Min(1) @Max(1440) Integer dailyGoalMinutes) {}
