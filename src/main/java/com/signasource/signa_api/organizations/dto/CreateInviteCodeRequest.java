package com.signasource.signa_api.organizations.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

public record CreateInviteCodeRequest(@Future Instant expiresAt, @Positive Integer maxUses) {}
