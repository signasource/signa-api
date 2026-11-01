package com.signasource.signa_api.organizations.dto;

/**
 * Mercado Pago's status of the payment ({@code approved}, {@code rejected}, ...) and the purchase.
 */
public record PaymentSyncResponse(String paymentStatus, PurchaseDetailResponse purchase) {}
