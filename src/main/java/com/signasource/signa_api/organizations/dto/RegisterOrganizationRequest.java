package com.signasource.signa_api.organizations.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterOrganizationRequest(
        @NotBlank String orgName,
        @NotBlank String cuit,
        @NotBlank @Email String contactEmail,
        String contactPhone,
        @NotNull @Min(1) Integer memberLimit,
        @NotBlank String adminName,
        @NotBlank @Size(min = 8) String adminPassword) {}
