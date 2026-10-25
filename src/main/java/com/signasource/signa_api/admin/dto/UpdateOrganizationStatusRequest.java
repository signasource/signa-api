package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrganizationStatusRequest(@NotNull OrganizationStatus status) {}
