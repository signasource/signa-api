package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record UpdateOrganizationStatusRequest(
        @NotNull OrganizationStatus status, List<UUID> courseIds) {}
