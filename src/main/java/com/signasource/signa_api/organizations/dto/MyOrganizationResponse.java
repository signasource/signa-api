package com.signasource.signa_api.organizations.dto;

import com.signasource.signa_api.learning.dto.CourseSummaryResponse;
import com.signasource.signa_api.organizations.entity.MemberRole;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MyOrganizationResponse(
        UUID id,
        String name,
        MemberRole role,
        Instant joinedAt,
        List<CourseSummaryResponse> courses) {}
