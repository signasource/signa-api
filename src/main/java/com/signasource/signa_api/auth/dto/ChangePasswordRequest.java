package com.signasource.signa_api.auth.dto;

import com.signasource.signa_api.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank String currentPassword, @ValidPassword String newPassword) {}
