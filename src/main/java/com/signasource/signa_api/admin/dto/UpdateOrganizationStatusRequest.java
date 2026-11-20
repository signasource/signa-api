package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code reason} is required when suspending and ignored otherwise. */
public record UpdateOrganizationStatusRequest(
        @NotNull OrganizationStatus status, @Size(max = 500) String reason) {

    public UpdateOrganizationStatusRequest(OrganizationStatus status) {
        this(status, null);
    }
}
