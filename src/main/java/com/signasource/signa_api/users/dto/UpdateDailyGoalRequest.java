package com.signasource.signa_api.users.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateDailyGoalRequest(@NotNull @Min(1) @Max(1440) Integer dailyGoalMinutes) {}
