package com.signasource.signa_api.gamification.service;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Stand-in used when {@code google-play.enabled=false}: trusts every token as a completed purchase.
 * Lets the redeem flow be exercised without Play Console. Never enable in production.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "google-play.enabled", havingValue = "false")
@RequiredArgsConstructor
public class DisabledGooglePlayPurchaseVerifier implements GooglePlayPurchaseVerifier {

    private final Environment environment;

    @PostConstruct
    void warn() {
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod")) {
            throw new IllegalStateException(
                    "google-play.enabled=false is not allowed on the prod profile: every purchase"
                            + " token would be accepted without verification");
        }
        log.warn(
                "Google Play verification is DISABLED: every purchase token will be accepted. Local"
                        + " development only.");
    }

    @Override
    public VerifiedProductPurchase verify(String productId, String purchaseToken) {
        return new VerifiedProductPurchase(
                PURCHASE_STATE_PURCHASED, 0, "local-" + purchaseToken, Instant.now());
    }
}
