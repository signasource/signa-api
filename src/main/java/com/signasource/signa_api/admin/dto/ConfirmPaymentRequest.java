package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.organizations.entity.PaymentMethod;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ConfirmPaymentRequest(
        @Size(max = 100) String paymentReference,
        PaymentMethod paymentMethod,
        LocalDate paymentDate) {}
