package com.signasource.signa_api.organizations.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PurchaseQuoteResponse(
        UUID courseId,
        String courseName,
        int signCount,
        int seats,
        int durationDays,
        BigDecimal pricePerSeat,
        BigDecimal totalAmount,
        String currency) {}
