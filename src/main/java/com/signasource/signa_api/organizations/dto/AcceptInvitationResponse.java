package com.signasource.signa_api.organizations.dto;

import java.time.Instant;
import java.util.UUID;

public record AcceptInvitationResponse(
        String organizationName,
        UUID courseId,
        String courseName,
        Instant activatedAt,
        Instant accessExpiresAt) {}
