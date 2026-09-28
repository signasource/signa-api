package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signasource.signa_api.gamification.service.GooglePlayPurchaseVerifier.VerifiedProductPurchase;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DisabledGooglePlayPurchaseVerifierTest {

    @Test
    void shouldRefuseToStartOnProdProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        DisabledGooglePlayPurchaseVerifier verifier = new DisabledGooglePlayPurchaseVerifier(env);

        assertThrows(IllegalStateException.class, verifier::warn);
    }

    @Test
    void shouldTrustAnyToken() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        DisabledGooglePlayPurchaseVerifier verifier = new DisabledGooglePlayPurchaseVerifier(env);
        verifier.warn();

        VerifiedProductPurchase result = verifier.verify("gems_pack_120", "whatever");

        assertTrue(result.isPurchased());
        assertFalse(result.isPending());
        assertFalse(result.isConsumed());
        assertEquals("local-whatever", result.orderId());
    }
}
