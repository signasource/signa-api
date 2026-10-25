package com.signasource.signa_api.organizations.service;

import java.math.BigDecimal;

/** The two Mercado Pago calls the seat checkout needs. */
public interface MercadoPagoClient {

    /** Mercado Pago's {@code status} of a credited payment. */
    String STATUS_APPROVED = "approved";

    /**
     * @throws com.signasource.signa_api.exceptions.PaymentProviderException if Mercado Pago is not
     *     configured or rejects the preference
     */
    CheckoutPreference createPreference(PreferenceRequest request);

    /**
     * @throws com.signasource.signa_api.exceptions.InvalidInputException if the payment does not
     *     exist for this seller
     * @throws com.signasource.signa_api.exceptions.PaymentProviderException if Mercado Pago is not
     *     configured or fails
     */
    Payment getPayment(String paymentId);

    /**
     * One item paid as {@code quantity × unitPrice}. {@code successUrl}, {@code failureUrl} and
     * {@code pendingUrl} are where Mercado Pago sends the buyer back.
     */
    record PreferenceRequest(
            String externalReference,
            String title,
            int quantity,
            BigDecimal unitPrice,
            String currency,
            String successUrl,
            String failureUrl,
            String pendingUrl) {}

    record CheckoutPreference(String id, String checkoutUrl) {}

    record Payment(
            String id,
            String status,
            String externalReference,
            BigDecimal transactionAmount,
            String currency) {

        public boolean isApproved() {
            return STATUS_APPROVED.equals(status);
        }
    }
}
