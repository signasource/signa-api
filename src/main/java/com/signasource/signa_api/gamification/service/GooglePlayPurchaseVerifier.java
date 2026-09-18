package com.signasource.signa_api.gamification.service;

import java.time.Instant;

/** Asks Google whether a purchase token really corresponds to a paid in-app product. */
public interface GooglePlayPurchaseVerifier {

    /** Google's {@code purchaseState}: the payment went through. */
    int PURCHASE_STATE_PURCHASED = 0;

    /** Google's {@code purchaseState}: the order was cancelled or refunded. */
    int PURCHASE_STATE_CANCELED = 1;

    /** Google's {@code purchaseState}: the user still has to complete the payment. */
    int PURCHASE_STATE_PENDING = 2;

    /** Google's {@code consumptionState}: the app already consumed this token. */
    int CONSUMPTION_STATE_CONSUMED = 1;

    /**
     * @throws com.signasource.signa_api.exceptions.InvalidInputException if Google rejects the
     *     token (unknown, malformed or for another product/package)
     */
    VerifiedProductPurchase verify(String productId, String purchaseToken);

    record VerifiedProductPurchase(
            int purchaseState, int consumptionState, String orderId, Instant purchaseTime) {

        public boolean isPurchased() {
            return purchaseState == PURCHASE_STATE_PURCHASED;
        }

        public boolean isPending() {
            return purchaseState == PURCHASE_STATE_PENDING;
        }

        public boolean isConsumed() {
            return consumptionState == CONSUMPTION_STATE_CONSUMED;
        }
    }
}
