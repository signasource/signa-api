package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.config.MercadoPagoProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Checks the {@code x-signature} header of a Mercado Pago webhook: {@code ts=<ts>,v1=<hmac>}, where
 * {@code hmac} is HMAC-SHA256 (hex) of {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;}
 * keyed with the webhook secret. Parts missing from the request are left out of the manifest, as
 * Mercado Pago documents.
 */
@Component
@RequiredArgsConstructor
public class MercadoPagoSignatureVerifier {

    private final MercadoPagoProperties properties;

    public boolean isEnabled() {
        return properties.webhookSecret() != null && !properties.webhookSecret().isBlank();
    }

    public boolean isValid(String signatureHeader, String requestId, String dataId) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String part : signatureHeader.split(",")) {
            String[] keyValue = part.strip().split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            if (keyValue[0].equals("ts")) {
                ts = keyValue[1].strip();
            } else if (keyValue[0].equals("v1")) {
                v1 = keyValue[1].strip();
            }
        }
        if (ts == null || v1 == null) {
            return false;
        }

        StringBuilder manifest = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            // Mercado Pago lowercases alphanumeric ids before signing.
            manifest.append("id:").append(dataId.toLowerCase(Locale.ROOT)).append(';');
        }
        if (requestId != null && !requestId.isBlank()) {
            manifest.append("request-id:").append(requestId).append(';');
        }
        manifest.append("ts:").append(ts).append(';');

        byte[] received;
        try {
            received = HexFormat.of().parseHex(v1.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(hmac(manifest.toString()), received);
    }

    private byte[] hmac(String manifest) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(
                    new SecretKeySpec(
                            properties.webhookSecret().getBytes(StandardCharsets.UTF_8),
                            "HmacSHA256"));
            return mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
