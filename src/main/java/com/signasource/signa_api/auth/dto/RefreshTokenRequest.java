package com.signasource.signa_api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(@NotBlank @Size(min = 10, max = 512) String refreshToken) {
}
