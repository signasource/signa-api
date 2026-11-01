package com.signasource.signa_api.organizations.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterOrganizationRequest(
        @NotBlank String orgName,
        @NotBlank String cuit,
        @NotBlank @Email String contactEmail,
        String contactPhone,
        @NotBlank String adminName,
        @NotBlank @Size(min = 8) String adminPassword) {}
