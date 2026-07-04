package com.signasource.signa_api.auth.dto;

import com.signasource.signa_api.validation.ValidPassword;

public record ResetPasswordRequest(@ValidPassword String newPassword) {}
