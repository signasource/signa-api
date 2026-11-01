package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminOrganizationResponse(
        UUID id,
        String name,
        OrganizationStatus status,
        String cuit,
        String contactEmail,
        String contactPhone,
        Instant createdAt,
        int activeMembers,
        String adminName,
        String adminEmail) {

    public static AdminOrganizationResponse from(
            Organization org, int activeMembers, String adminName, String adminEmail) {
        return new AdminOrganizationResponse(
                org.getId(),
                org.getName(),
                org.getStatus(),
                org.getCuit(),
                org.getContactEmail(),
                org.getContactPhone(),
                org.getCreatedAt(),
                activeMembers,
                adminName,
                adminEmail);
    }
}
