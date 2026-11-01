package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signasource.signa_api.organizations.service.MercadoPagoClient.Payment;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MercadoPagoPaymentTest {

    private static Payment payment(String status, String detail) {
        return new Payment("1", status, detail, "ref", BigDecimal.ONE, "ARS");
    }

    @Test
    void shouldReadMercadoPagoStatuses() {
        assertTrue(payment("approved", "accredited").isApproved());
        assertTrue(payment("refunded", "refunded").isRefunded());
        assertTrue(payment("charged_back", "settled").isChargedBack());
        assertTrue(payment("in_mediation", "pending").isInMediation());
        assertTrue(payment("approved", "partially_refunded").isPartiallyRefunded());
        assertFalse(payment("refunded", "partially_refunded").isPartiallyRefunded());
        assertFalse(payment("approved", "accredited").isPartiallyRefunded());
    }
}
