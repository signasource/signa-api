package com.signasource.signa_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mercado Pago Checkout Pro, used to pay organization seat purchases.
 *
 * <p>{@code accessToken} is the seller's private token (a {@code TEST-} one in sandbox). {@code
 * webhookSecret} signs the notifications ({@code x-signature}); when it is empty the signature is
 * not checked, which is acceptable only locally because every notification is re-read from the API
 * anyway. {@code notificationUrl} is the public URL of {@code POST /payments/mercado-pago/webhook};
 * empty means the webhook is configured in the Mercado Pago dashboard instead.
 */
@ConfigurationProperties(prefix = "mercado-pago")
public record MercadoPagoProperties(
        String apiUrl, String accessToken, String webhookSecret, String notificationUrl) {

    public boolean isConfigured() {
        return accessToken != null && !accessToken.isBlank();
    }
}
