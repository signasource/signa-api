package com.signasource.signa_api.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminRefundRequestResponse(
        UUID id,
        String purchaseType,
        UUID purchaseId,
        String reason,
        String channel,
        String message,
        String note,
        String status,
        String resolvedByName,
        Instant resolvedAt,
        Instant createdAt,
        // requester info
        String requesterName,
        String requesterUsername,
        String requesterEmail,
        // linked purchase summary (denormalized for the list view)
        String productName,
        String productDetail,
        BigDecimal amount,
        String currency,
        String purchaseStatus,
        Instant purchasePaidAt) {}
