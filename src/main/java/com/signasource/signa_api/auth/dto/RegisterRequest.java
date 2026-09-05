package com.signasource.signa_api.auth.dto;

import com.signasource.signa_api.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @Email @NotBlank @Size(max = 255) String email,
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[a-zA-Z0-9_]+$") String username,
        @ValidPassword String password,
        @NotBlank @Size(min = 2, max = 100) @Pattern(regexp = "^[\\p{L} .'-]+$") String name,
        @NotBlank @Size(min = 2, max = 100) @Pattern(regexp = "^[\\p{L} .'-]+$") String lastName) {}
