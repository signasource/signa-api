package com.signasource.signa_api.organizations.dto;

import com.signasource.signa_api.learning.dto.CourseSummaryResponse;
import java.time.Instant;

public record RedeemInviteCodeResponse(
        String organizationName,
        CourseSummaryResponse course,
        boolean alreadyEnrolled,
        Instant accessExpiresAt) {}
