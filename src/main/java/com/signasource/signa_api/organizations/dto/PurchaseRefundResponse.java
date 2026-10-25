package com.signasource.signa_api.organizations.dto;

import com.signasource.signa_api.organizations.entity.PurchaseRefund;
import com.signasource.signa_api.organizations.entity.RefundReason;
import com.signasource.signa_api.organizations.entity.RefundStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PurchaseRefundResponse(
        BigDecimal amount,
        String currency,
        RefundReason reason,
        RefundStatus status,
        Instant createdAt,
        Instant refundedAt,
        String note) {

    public static PurchaseRefundResponse from(PurchaseRefund refund) {
        return new PurchaseRefundResponse(
                refund.getAmount(),
                refund.getCurrency(),
                refund.getReason(),
                refund.getStatus(),
                refund.getCreatedAt(),
                refund.getRefundedAt(),
                refund.getNote());
    }
}
