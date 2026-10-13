package com.signasource.signa_api.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateAdminRequest(
        @NotBlank @Email String email,
        @NotBlank String name,
        @NotBlank String lastName,
        @NotBlank String password) {}
