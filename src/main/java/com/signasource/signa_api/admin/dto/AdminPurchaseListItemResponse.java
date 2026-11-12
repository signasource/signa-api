package com.signasource.signa_api.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row in the unified admin purchases list. Covers SEATS (org seat purchases), GEMS (gem pack
 * orders) and COURSE (individual course orders) in a single shape.
 */
public record AdminPurchaseListItemResponse(
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
        boolean pendingRefundRequest) {}
