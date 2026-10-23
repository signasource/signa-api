package com.signasource.signa_api.auth.dto;

import com.signasource.signa_api.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @Email @NotBlank @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "\\d{6}") String code,
        @ValidPassword String newPassword) {}
