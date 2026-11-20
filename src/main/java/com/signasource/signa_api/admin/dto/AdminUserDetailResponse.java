package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.users.entity.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AdminUserDetailResponse(
        UUID id,
        String email,
        String username,
        String name,
        String lastName,
        Role role,
        boolean enabled,
        boolean verified,
        String organizationName,
        String authProvider,
        int currentStreak,
        LocalDate lastStreakDate,
        LocalDate lastSeenDate,
        String avatar,
        Integer overallAccuracyPercent,
        Boolean isEmployeeOfOrg,
        List<UserEnrollmentInfo> enrollments,
        OrgAdminDetail orgAdminDetail) {

    public record UserEnrollmentInfo(
            UUID courseVersionId,
            String courseName,
            String version,
            String courseVersionStatus,
            String enrollmentStatus,
            boolean grantsAccess,
            Instant startedAt,
            Instant completedAt) {}

    public record OrgAdminDetail(
            UUID orgId, String orgStatus, int memberCount, List<OrgPurchaseSummary> purchases) {}

    public record OrgPurchaseSummary(
            String courseName,
            int totalSeats,
            String purchaseStatus,
            Instant createdAt,
            Instant paidAt) {}
}
