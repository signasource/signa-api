package com.signasource.signa_api.organizations.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterOrganizationRequest(
        @NotBlank String orgName,
        @NotBlank String cuit,
        @NotBlank @Email String contactEmail,
        /* 8–15 digits (E.164 max) with optional leading "+" and spaces, dots, dashes or parentheses. */
        @NotBlank @Size(max = 50) @Pattern(regexp = PHONE_PATTERN) String contactPhone,
        @NotBlank String adminName,
        @NotBlank @Size(min = 8) String adminPassword) {

    public static final String PHONE_PATTERN = "^\\s*\\+?(?:[\\s().-]*\\d){8,15}[\\s().-]*$";
}
