package com.signasource.signa_api.admin.dto;

public record AdminStatsResponse(
        long activeOrganizations,
        long pendingOrganizations,
        long openSignReports,
        long totalUsers,
        long activeUsersLast7Days,
        long waitlistCount,
        long totalSigns) {}
