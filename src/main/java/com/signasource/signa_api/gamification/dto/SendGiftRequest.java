package com.signasource.signa_api.gamification.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SendGiftRequest(
        @NotNull UUID shopItemId, @NotNull UUID recipientUserId, @Size(max = 500) String message) {}
