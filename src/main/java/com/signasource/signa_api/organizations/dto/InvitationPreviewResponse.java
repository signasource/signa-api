package com.signasource.signa_api.organizations.dto;

import com.signasource.signa_api.organizations.entity.SeatStatus;
import java.time.Instant;

public record InvitationPreviewResponse(
        String organizationName,
        String courseName,
        String email,
        SeatStatus status,
        Instant invitationDeadline,
        int durationDays,
        boolean accountExists) {}
