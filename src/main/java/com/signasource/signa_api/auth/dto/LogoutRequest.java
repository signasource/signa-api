package com.signasource.signa_api.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(@NotBlank String refreshToken, String deviceToken) {
}
