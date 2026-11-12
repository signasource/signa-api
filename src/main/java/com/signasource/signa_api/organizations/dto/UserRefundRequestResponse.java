package com.signasource.signa_api.organizations.dto;

import com.signasource.signa_api.organizations.entity.RefundRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record UserRefundRequestResponse(
        UUID id,
        String purchaseType,
        UUID purchaseId,
        String reason,
        String message,
        String status,
        Instant createdAt,
        String productName,
        BigDecimal amount,
        String currency) {

    public static UserRefundRequestResponse from(
            RefundRequest r, String productName, BigDecimal amount, String currency) {
        return new UserRefundRequestResponse(
                r.getId(),
                r.getPurchaseType(),
                r.getPurchaseId(),
                r.getReason().name(),
                r.getMessage(),
                r.getStatus().name(),
                r.getCreatedAt(),
                productName,
                amount,
                currency);
    }
}
