package com.signasource.signa_api.organizations.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import java.util.UUID;

public record CreateInviteCodeRequest(
        @NotNull UUID courseId, Instant expiresAt, @Positive Integer maxUses) {}
