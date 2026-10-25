package com.signasource.signa_api.organizations.entity;

/** Why an approved payment could not be applied to its purchase and was given back. */
public enum RefundReason {
    /** Paid after the organization cancelled the purchase. */
    PURCHASE_CANCELLED,
    /** The purchase was already paid by another payment (e.g. paid twice from two tabs). */
    ALREADY_PAID,
    /** The amount or currency differs from the purchase total. */
    AMOUNT_MISMATCH
}
