package com.signasource.signa_api.organizations.dto;

import com.signasource.signa_api.organizations.entity.MemberStatus;
import java.time.Instant;
import java.util.UUID;

public record OrganizationMemberSummaryResponse(
        UUID userId,
        String name,
        String lastName,
        String email,
        MemberStatus status,
        Instant joinedAt,
        Instant lastActivityAt,
        int progressPercentage,
        String currentModule) {}
