package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signasource.signa_api.gamification.service.GooglePlayPurchaseVerifier.VerifiedProductPurchase;
import org.junit.jupiter.api.Test;

class DisabledGooglePlayPurchaseVerifierTest {

    @Test
    void shouldTrustAnyToken() {
        DisabledGooglePlayPurchaseVerifier verifier = new DisabledGooglePlayPurchaseVerifier();
        verifier.warn();

        VerifiedProductPurchase result = verifier.verify("gems_pack_120", "whatever");

        assertTrue(result.isPurchased());
        assertFalse(result.isPending());
        assertFalse(result.isConsumed());
        assertEquals("local-whatever", result.orderId());
    }
}
