package com.signasource.signa_api.auth.dto;

import jakarta.validation.constraints.Email;

public record ForgotPasswordRequest(@Email String email) {
}
