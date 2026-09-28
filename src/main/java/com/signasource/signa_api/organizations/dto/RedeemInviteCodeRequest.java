package com.signasource.signa_api.organizations.dto;

import jakarta.validation.constraints.NotBlank;

public record RedeemInviteCodeRequest(@NotBlank String code) {}
