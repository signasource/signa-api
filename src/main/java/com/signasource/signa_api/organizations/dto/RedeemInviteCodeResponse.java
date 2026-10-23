package com.signasource.signa_api.organizations.dto;

import com.signasource.signa_api.learning.dto.CourseSummaryResponse;
import com.signasource.signa_api.organizations.entity.MemberRole;
import java.time.Instant;
import java.util.List;

public record RedeemInviteCodeResponse(
        String organizationName,
        List<CourseSummaryResponse> courses,
        boolean alreadyMember,
        Instant accessExpiresAt,
        MemberRole role) {}
