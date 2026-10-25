package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PurchasePolicyTest {

    private final PurchasePolicy policy =
            new PurchasePolicy(
                    "ARS", new BigDecimal("5"), new BigDecimal("5000"), 15, 60, 10, 5, 24, 500);

    @Test
    void shouldPriceBySignsAndDays() {
        assertEquals(new BigDecimal("15000.00"), policy.pricePerSeat(100, 30));
    }

    @Test
    void shouldNeverGoBelowTheMinimumPricePerSeat() {
        assertEquals(new BigDecimal("5000.00"), policy.pricePerSeat(10, 15));
    }

    @Test
    void shouldMultiplyTheTotalBySeats() {
        assertEquals(new BigDecimal("45000.00"), policy.total(new BigDecimal("15000"), 3));
    }

    @Test
    void shouldAcceptOnlyDurationsInsideTheRange() {
        assertFalse(policy.isValidDuration(14));
        assertTrue(policy.isValidDuration(15));
        assertTrue(policy.isValidDuration(60));
        assertFalse(policy.isValidDuration(61));
    }

    @Test
    void shouldExposeTheWindowsAsDurations() {
        assertEquals(Duration.ofDays(10), policy.invitationWindow());
        assertEquals(Duration.ofDays(5), policy.autoReminderDelay());
        assertEquals(Duration.ofHours(24), policy.manualReminderCooldown());
    }
}
