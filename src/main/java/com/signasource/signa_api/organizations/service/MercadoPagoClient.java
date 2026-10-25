package com.signasource.signa_api.organizations.service;

import java.math.BigDecimal;

/** The Mercado Pago calls the seat checkout needs: pay, read, refund and expire. */
public interface MercadoPagoClient {

    /** Mercado Pago's {@code status} of a credited payment. */
    String STATUS_APPROVED = "approved";

    /** Mercado Pago's {@code status} of a payment given back in full. */
    String STATUS_REFUNDED = "refunded";

    /** Mercado Pago's {@code status} of a payment the buyer disputed and won with their bank. */
    String STATUS_CHARGED_BACK = "charged_back";

    /** Mercado Pago's {@code status} while a dispute (claim or chargeback) is open. */
    String STATUS_IN_MEDIATION = "in_mediation";

    /** Mercado Pago's {@code status_detail} of an approved payment partly given back. */
    String DETAIL_PARTIALLY_REFUNDED = "partially_refunded";

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
     * Refunds the whole payment and returns Mercado Pago's refund id. Idempotent per payment.
     *
     * @throws com.signasource.signa_api.exceptions.PaymentProviderException if Mercado Pago is not
     *     configured or rejects the refund
     */
    String refund(String paymentId);

    /** Makes a preference stop accepting payments (best effort before it is used). */
    void expirePreference(String preferenceId);

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
            String statusDetail,
            String externalReference,
            BigDecimal transactionAmount,
            String currency) {

        public boolean isApproved() {
            return STATUS_APPROVED.equals(status);
        }

        public boolean isRefunded() {
            return STATUS_REFUNDED.equals(status);
        }

        public boolean isChargedBack() {
            return STATUS_CHARGED_BACK.equals(status);
        }

        public boolean isInMediation() {
            return STATUS_IN_MEDIATION.equals(status);
        }

        public boolean isPartiallyRefunded() {
            return isApproved() && DETAIL_PARTIALLY_REFUNDED.equals(statusDetail);
        }
    }
}
