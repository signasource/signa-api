package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PurchasePolicyTest {

    private final PurchasePolicy policy = new PurchasePolicy(15, 60, 10, 5, 24, 500, 10);

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
