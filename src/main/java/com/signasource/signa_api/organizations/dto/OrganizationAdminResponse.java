package com.signasource.signa_api.organizations.dto;

import com.signasource.signa_api.organizations.entity.OrganizationMember;
import java.time.Instant;
import java.util.UUID;

public record OrganizationAdminResponse(
        UUID userId, String name, String lastName, String email, Instant joinedAt) {
    public static OrganizationAdminResponse from(OrganizationMember member) {
        return new OrganizationAdminResponse(
                member.getUser().getId(),
                member.getUser().getName(),
                member.getUser().getLastName(),
                member.getUser().getEmail(),
                member.getJoinedAt());
    }
}
