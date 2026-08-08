package com.signasource.signa_api.learning.dto;

import jakarta.validation.constraints.NotNull;

public record ExerciseAttemptRequest(@NotNull Boolean isCorrect) {}
