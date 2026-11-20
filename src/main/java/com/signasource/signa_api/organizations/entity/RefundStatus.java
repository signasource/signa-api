package com.signasource.signa_api.organizations.entity;

public enum RefundStatus {
    /** Recorded; Mercado Pago has not confirmed the refund yet (it is retried). */
    PENDING,
    REFUNDED
}
