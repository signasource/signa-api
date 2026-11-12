package com.signasource.signa_api.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full purchase detail for the admin back-office, including type-specific fields and requests. */
public record AdminPurchaseDetailResponse(
        UUID id,
        String type,
        String productName,
        String productDetail,
        String buyerName,
        String buyerSub,
        String buyerEmail,
        BigDecimal amount,
        String currency,
        String status,
        Instant createdAt,
        Instant paidAt,
        Instant refundedAt,
        String mpOrderId,
        String paymentMethod,
        String manualRef,
        String confirmedByName,
        Instant confirmedAt,
        String externalRef,
        // SEATS-specific
        UUID organizationId,
        Integer seats,
        Integer durationDays,
        Integer discountPercent,
        // GEMS-specific
        Integer gems,
        // shared
        List<AdminRefundRequestResponse> refundRequests) {}
