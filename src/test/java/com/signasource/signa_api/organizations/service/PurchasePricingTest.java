package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PurchasePricingTest {

    private final PurchasePricing pricing =
            new PurchasePricing(
                    "ARS",
                    new BigDecimal("6000"),
                    new BigDecimal("400"),
                    30,
                    0.85,
                    BigDecimal.TEN,
                    "25:15, 10:10,100:25,50:20");

    @Test
    void shouldChargeTheReferencePriceForThirtyDays() {
        assertEquals(new BigDecimal("10000.00"), pricing.listPricePerSeat(10, 30));
        assertEquals(new BigDecimal("18000.00"), pricing.listPricePerSeat(30, 30));
    }

    @Test
    void shouldChargeMoreForACourseWithMoreSigns() {
        assertTrue(
                pricing.listPricePerSeat(30, 30).compareTo(pricing.listPricePerSeat(10, 30)) > 0);
    }

    @Test
    void shouldMakeLongerAccessCheaperPerDay() {
        assertEquals(new BigDecimal("5550.00"), pricing.listPricePerSeat(10, 15));
        assertEquals(new BigDecimal("18030.00"), pricing.listPricePerSeat(10, 60));
        BigDecimal perDayShort = pricing.listPricePerSeat(10, 15).divide(BigDecimal.valueOf(15));
        BigDecimal perDayLong = pricing.listPricePerSeat(10, 60).divide(BigDecimal.valueOf(60));
        assertTrue(perDayLong.compareTo(perDayShort) < 0);
    }

    @Test
    void shouldApplyTheBestVolumeTierReached() {
        assertEquals(0, pricing.discountPercent(9));
        assertEquals(10, pricing.discountPercent(10));
        assertEquals(15, pricing.discountPercent(49));
        assertEquals(20, pricing.discountPercent(50));
        assertEquals(25, pricing.discountPercent(500));
    }

    @Test
    void shouldQuoteTheDiscountedTotal() {
        PurchasePricing.Quote quote = pricing.quote(30, 30, 15);

        assertEquals(new BigDecimal("18000.00"), quote.listPricePerSeat());
        assertEquals(10, quote.discountPercent());
        assertEquals(new BigDecimal("16200.00"), quote.pricePerSeat());
        assertEquals(new BigDecimal("270000.00"), quote.subtotal());
        assertEquals(new BigDecimal("27000.00"), quote.discountAmount());
        assertEquals(new BigDecimal("243000.00"), quote.totalAmount());
    }

    @Test
    void shouldRoundTheDiscountedSeatPrice() {
        // 5550 × 0.85 = 4717.5 → 4720
        assertEquals(new BigDecimal("4720.00"), pricing.quote(10, 15, 25).pricePerSeat());
    }

    @Test
    void shouldQuoteWithoutDiscountWhenNoTierIsConfigured() {
        PurchasePricing flat =
                new PurchasePricing(
                        "ARS",
                        new BigDecimal("6000"),
                        new BigDecimal("400"),
                        30,
                        0.85,
                        BigDecimal.TEN,
                        "");

        assertEquals(0, flat.quote(10, 30, 400).discountPercent());
        assertTrue(flat.getVolumeDiscounts().isEmpty());
    }

    @Test
    void shouldRejectMalformedDiscounts() {
        assertThrows(IllegalArgumentException.class, () -> withDiscounts("10"));
        assertThrows(IllegalArgumentException.class, () -> withDiscounts("10:100"));
        assertThrows(IllegalArgumentException.class, () -> withDiscounts("10:-1"));
    }

    private static PurchasePricing withDiscounts(String discounts) {
        return new PurchasePricing(
                "ARS", BigDecimal.ONE, BigDecimal.ONE, 30, 1, BigDecimal.ONE, discounts);
    }
}
