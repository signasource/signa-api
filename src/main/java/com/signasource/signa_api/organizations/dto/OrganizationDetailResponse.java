package com.signasource.signa_api.organizations.dto;

import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import java.time.Instant;
import java.util.UUID;

public record OrganizationDetailResponse(
        UUID id,
        String name,
        OrganizationStatus status,
        String cuit,
        String contactEmail,
        String contactPhone,
        Integer memberLimit,
        long activeMembers,
        Instant createdAt) {
    public static OrganizationDetailResponse from(Organization organization, long activeMembers) {
        return new OrganizationDetailResponse(
                organization.getId(),
                organization.getName(),
                organization.getStatus(),
                organization.getCuit(),
                organization.getContactEmail(),
                organization.getContactPhone(),
                organization.getMemberLimit(),
                activeMembers,
                organization.getCreatedAt());
    }
}
