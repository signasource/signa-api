package com.signasource.signa_api.gamification.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateGemOrderRequest(@NotNull UUID packId) {}
