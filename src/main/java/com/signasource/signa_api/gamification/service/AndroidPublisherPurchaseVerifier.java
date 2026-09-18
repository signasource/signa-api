package com.signasource.signa_api.gamification.service;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.ProductPurchase;
import com.signasource.signa_api.config.GooglePlayProperties;
import com.signasource.signa_api.exceptions.InvalidInputException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Real verifier: calls {@code purchases.products.get} on the Google Play Developer API. Network
 * I/O, so it must never run inside a database transaction.
 */
@Service
@ConditionalOnProperty(name = "google-play.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AndroidPublisherPurchaseVerifier implements GooglePlayPurchaseVerifier {

    private final AndroidPublisher androidPublisher;
    private final GooglePlayProperties properties;

    @Override
    public VerifiedProductPurchase verify(String productId, String purchaseToken) {
        ProductPurchase purchase;
        try {
            purchase =
                    androidPublisher
                            .purchases()
                            .products()
                            .get(properties.packageName(), productId, purchaseToken)
                            .execute();
        } catch (GoogleJsonResponseException e) {
            // 400 = malformed token, 404 = token not for this product/package.
            if (e.getStatusCode() == 400 || e.getStatusCode() == 404) {
                throw new InvalidInputException("Invalid purchase token");
            }
            throw new UncheckedIOException("Google Play verification failed", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Google Play verification failed", e);
        }

        return new VerifiedProductPurchase(
                purchase.getPurchaseState() == null ? -1 : purchase.getPurchaseState(),
                purchase.getConsumptionState() == null ? 0 : purchase.getConsumptionState(),
                purchase.getOrderId(),
                purchase.getPurchaseTimeMillis() == null
                        ? Instant.now()
                        : Instant.ofEpochMilli(purchase.getPurchaseTimeMillis()));
    }
}
