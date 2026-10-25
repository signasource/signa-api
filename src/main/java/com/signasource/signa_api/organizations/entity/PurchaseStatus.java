package com.signasource.signa_api.organizations.entity;

public enum PurchaseStatus {
    PENDING_PAYMENT,
    PAID,
    CANCELLED,
    /**
     * Paid and then given back (withdrawal by the organization, a refund made in Mercado Pago or a
     * chargeback): pending invitations were cancelled and active accesses ended.
     */
    REFUNDED
}
