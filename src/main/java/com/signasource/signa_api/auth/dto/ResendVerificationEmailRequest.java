package com.signasource.signa_api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendVerificationEmailRequest(@Email @NotBlank @Size(max = 255) String email) {
}
