package com.signasource.signa_api.organizations.dto;

import java.util.UUID;

public record RegisterOrganizationResponse(
        UUID organizationId, String organizationName, String status) {}
