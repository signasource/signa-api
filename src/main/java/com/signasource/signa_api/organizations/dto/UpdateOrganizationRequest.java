package com.signasource.signa_api.organizations.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Partial update: a {@code null} field is left unchanged. The status is changed from the admin
 * panel ({@code PATCH /admin/organizations/{id}/status}), which also contracts the courses.
 */
public record UpdateOrganizationRequest(
        @Size(min = 1, max = 150) String name, @Min(1) Integer memberLimit) {}
