package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signasource.signa_api.config.MercadoPagoProperties;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class MercadoPagoSignatureVerifierTest {

    private static final String SECRET = "webhook-secret";

    private final MercadoPagoSignatureVerifier verifier =
            new MercadoPagoSignatureVerifier(
                    new MercadoPagoProperties(null, "token", SECRET, null));

    private static String sign(String manifest) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void shouldAcceptAValidSignature() throws Exception {
        String v1 = sign("id:123456;request-id:req-1;ts:1704908010;");

        assertTrue(verifier.isValid("ts=1704908010,v1=" + v1, "req-1", "123456"));
    }

    @Test
    void shouldLowercaseAlphanumericIdsAndSkipAMissingRequestId() throws Exception {
        String v1 = sign("id:abc123;ts:1704908010;");

        assertTrue(verifier.isValid(" ts=1704908010 , v1=" + v1.toUpperCase(), null, "ABC123"));
    }

    @Test
    void shouldRejectATamperedOrMalformedSignature() throws Exception {
        String v1 = sign("id:123456;request-id:req-1;ts:1704908010;");

        assertFalse(verifier.isValid("ts=1704908010,v1=" + v1, "req-1", "999999"));
        assertFalse(verifier.isValid("ts=1704908010,v1=not-hex", "req-1", "123456"));
        assertFalse(verifier.isValid("v1=" + v1, "req-1", "123456"));
        assertFalse(verifier.isValid("ts=1704908010,garbage", "req-1", "123456"));
        assertFalse(verifier.isValid(null, "req-1", "123456"));
        assertFalse(verifier.isValid(" ", "req-1", "123456"));
    }

    @Test
    void shouldBeEnabledOnlyWithASecret() {
        assertTrue(verifier.isEnabled());
        assertFalse(
                new MercadoPagoSignatureVerifier(new MercadoPagoProperties(null, "t", " ", null))
                        .isEnabled());
        assertFalse(
                new MercadoPagoSignatureVerifier(new MercadoPagoProperties(null, "t", null, null))
                        .isEnabled());
    }
}
