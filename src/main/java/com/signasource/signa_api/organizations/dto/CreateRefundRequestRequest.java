package com.signasource.signa_api.organizations.dto;

import com.signasource.signa_api.organizations.entity.RefundRequestReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRefundRequestRequest(
        @NotNull RefundRequestReason reason,
        @NotBlank @Size(max = 2000) String message) {}
