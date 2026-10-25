package com.signasource.signa_api.organizations.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** The {@code payment_id} Mercado Pago appended to the return URL. */
public record SyncPaymentRequest(
        @NotBlank @Pattern(regexp = "[0-9]{1,30}", message = "paymentId must be numeric") String paymentId) {}
