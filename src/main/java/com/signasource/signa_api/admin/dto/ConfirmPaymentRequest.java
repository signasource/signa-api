package com.signasource.signa_api.admin.dto;

import jakarta.validation.constraints.Size;

public record ConfirmPaymentRequest(@Size(max = 100) String paymentReference) {}
