package com.signasource.signa_api.organizations.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What a purchase would cost. {@code subtotal} is {@code listPricePerSeat × seats}; {@code
 * totalAmount} is {@code pricePerSeat × seats}, after the volume discount.
 */
public record PurchaseQuoteResponse(
        UUID courseId,
        String courseName,
        int signCount,
        int seats,
        int durationDays,
        BigDecimal listPricePerSeat,
        int discountPercent,
        BigDecimal pricePerSeat,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String currency) {}
