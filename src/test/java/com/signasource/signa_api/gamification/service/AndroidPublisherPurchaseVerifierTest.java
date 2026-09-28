package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.androidpublisher.AndroidPublisher;
import com.google.api.services.androidpublisher.model.ProductPurchase;
import com.signasource.signa_api.config.GooglePlayProperties;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.gamification.service.GooglePlayPurchaseVerifier.VerifiedProductPurchase;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AndroidPublisherPurchaseVerifierTest {

    private static final String PACKAGE = "com.signasource.signamobile";
    private static final String PRODUCT_ID = "gems_pack_300";
    private static final String TOKEN = "token";

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private AndroidPublisher androidPublisher;

    private AndroidPublisherPurchaseVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier =
                new AndroidPublisherPurchaseVerifier(
                        androidPublisher, new GooglePlayProperties(true, PACKAGE, ""));
    }

    private static GoogleJsonResponseException googleError(int status) {
        HttpResponseException.Builder builder =
                new HttpResponseException.Builder(status, "error", new HttpHeaders());
        return new GoogleJsonResponseException(builder, null);
    }

    @Test
    void shouldMapGoogleResponse() throws IOException {
        ProductPurchase purchase =
                new ProductPurchase()
                        .setPurchaseState(0)
                        .setConsumptionState(0)
                        .setOrderId("GPA.1234")
                        .setPurchaseTimeMillis(1_700_000_000_000L);
        when(androidPublisher.purchases().products().get(PACKAGE, PRODUCT_ID, TOKEN).execute())
                .thenReturn(purchase);

        VerifiedProductPurchase result = verifier.verify(PRODUCT_ID, TOKEN);

        assertTrue(result.isPurchased());
        assertEquals("GPA.1234", result.orderId());
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000L), result.purchaseTime());
    }

    @Test
    void shouldRejectTokenBoughtForAnotherProduct() throws IOException {
        ProductPurchase purchase =
                new ProductPurchase().setPurchaseState(0).setProductId("gems_pack_120");
        when(androidPublisher.purchases().products().get(PACKAGE, PRODUCT_ID, TOKEN).execute())
                .thenReturn(purchase);

        assertThrows(InvalidInputException.class, () -> verifier.verify(PRODUCT_ID, TOKEN));
    }

    @Test
    void shouldDefaultMissingFields() throws IOException {
        when(androidPublisher.purchases().products().get(PACKAGE, PRODUCT_ID, TOKEN).execute())
                .thenReturn(new ProductPurchase());

        VerifiedProductPurchase result = verifier.verify(PRODUCT_ID, TOKEN);

        assertEquals(-1, result.purchaseState());
        assertEquals(0, result.consumptionState());
        assertTrue(result.purchaseTime().isBefore(Instant.now().plusSeconds(1)));
    }

    @Test
    void shouldRejectUnknownTokenAsInvalidInput() throws IOException {
        when(androidPublisher.purchases().products().get(PACKAGE, PRODUCT_ID, TOKEN).execute())
                .thenThrow(googleError(404));

        assertThrows(InvalidInputException.class, () -> verifier.verify(PRODUCT_ID, TOKEN));
    }

    @Test
    void shouldPropagateOtherGoogleErrors() throws IOException {
        when(androidPublisher.purchases().products().get(PACKAGE, PRODUCT_ID, TOKEN).execute())
                .thenThrow(googleError(503));

        assertThrows(UncheckedIOException.class, () -> verifier.verify(PRODUCT_ID, TOKEN));
    }

    @Test
    void shouldPropagateIoErrors() throws IOException {
        when(androidPublisher.purchases().products().get(PACKAGE, PRODUCT_ID, TOKEN).execute())
                .thenThrow(new IOException("network down"));

        assertThrows(UncheckedIOException.class, () -> verifier.verify(PRODUCT_ID, TOKEN));
    }
}
