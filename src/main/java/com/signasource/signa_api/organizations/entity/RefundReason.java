package com.signasource.signa_api.organizations.entity;

/** Why a Mercado Pago payment was given back. */
public enum RefundReason {
    /** Paid after the purchase was cancelled or refunded. */
    PURCHASE_CANCELLED,
    /** The purchase was already paid by another payment (e.g. paid twice from two tabs). */
    ALREADY_PAID,
    /** The amount or currency differs from the purchase total. */
    AMOUNT_MISMATCH,
    /** The organization cancelled the purchase within the withdrawal period. */
    BUYER_REQUESTED,
    /** Someone refunded the payment from Mercado Pago itself, outside Signa. */
    EXTERNAL_REFUND,
    /** The buyer disputed the charge with their bank and Mercado Pago took the money back. */
    CHARGEBACK;

    /** Whether giving this payment back undoes its purchase (the payment had paid it). */
    public boolean revokesPurchase() {
        return this == BUYER_REQUESTED || this == EXTERNAL_REFUND || this == CHARGEBACK;
    }
}
