package com.signasource.signa_api.gamification.entity;

public enum GemOrderStatus {
    /** Checkout created, not paid yet. */
    PENDING,
    /** Payment confirmed by Mercado Pago and the gems credited. */
    PAID,
    /** Payment given back (refund or chargeback) after being credited. */
    REFUNDED
}
