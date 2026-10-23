package com.signasource.signa_api.organizations.dto;

import com.signasource.signa_api.organizations.entity.Organization;
import java.util.UUID;

public record OrganizationResponse(UUID id, String name) {
    public static OrganizationResponse from(Organization organization) {
        return new OrganizationResponse(organization.getId(), organization.getName());
    }
}
