package com.signasource.signa_api.learning.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PracticeAttemptRequest(@NotNull UUID lessonBlockId, @NotNull Boolean isCorrect) {}
