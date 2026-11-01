package com.signasource.signa_api.organizations.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Price of a seat purchase: a course with more signs, a longer access or fewer seats cost more.
 *
 * <p>List price per seat = {@code (basePricePerSeat + pricePerSign × signs) × (days /
 * referenceDays)^durationExponent}. The exponent is below 1 so a longer access is cheaper per day.
 * Then the best volume tier the seat count reaches is discounted. Prices are rounded to {@code
 * roundTo} (whole pesos look better on an invoice than cents).
 */
@Getter
@Component
public class PurchasePricing {

    private final String currency;
    private final BigDecimal basePricePerSeat;
    private final BigDecimal pricePerSign;
    private final int referenceDays;
    private final double durationExponent;
    private final BigDecimal roundTo;
    private final List<VolumeDiscount> volumeDiscounts;

    public PurchasePricing(
            @Value("${organizations.pricing.currency:ARS}") String currency,
            @Value("${organizations.pricing.base-price-per-seat:6000}") BigDecimal basePricePerSeat,
            @Value("${organizations.pricing.price-per-sign:400}") BigDecimal pricePerSign,
            @Value("${organizations.pricing.reference-days:30}") int referenceDays,
            @Value("${organizations.pricing.duration-exponent:0.85}") double durationExponent,
            @Value("${organizations.pricing.round-to:10}") BigDecimal roundTo,
            @Value("${organizations.pricing.volume-discounts:10:10,25:15,50:20,100:25}")
                    String volumeDiscounts) {
        this.currency = currency;
        this.basePricePerSeat = basePricePerSeat;
        this.pricePerSign = pricePerSign;
        this.referenceDays = referenceDays;
        this.durationExponent = durationExponent;
        this.roundTo = roundTo;
        this.volumeDiscounts = parseDiscounts(volumeDiscounts);
    }

    public Quote quote(int signCount, int durationDays, int seats) {
        BigDecimal listPricePerSeat = listPricePerSeat(signCount, durationDays);
        int discountPercent = discountPercent(seats);
        BigDecimal pricePerSeat =
                round(
                        listPricePerSeat
                                .multiply(BigDecimal.valueOf(100L - discountPercent))
                                .divide(BigDecimal.valueOf(100)));
        BigDecimal count = BigDecimal.valueOf(seats);
        BigDecimal subtotal = scale(listPricePerSeat.multiply(count));
        BigDecimal total = scale(pricePerSeat.multiply(count));
        return new Quote(
                listPricePerSeat,
                discountPercent,
                pricePerSeat,
                subtotal,
                subtotal.subtract(total),
                total);
    }

    BigDecimal listPricePerSeat(int signCount, int durationDays) {
        BigDecimal content =
                basePricePerSeat.add(pricePerSign.multiply(BigDecimal.valueOf(signCount)));
        double durationFactor = Math.pow((double) durationDays / referenceDays, durationExponent);
        return round(content.multiply(BigDecimal.valueOf(durationFactor)));
    }

    int discountPercent(int seats) {
        return volumeDiscounts.stream()
                .filter(tier -> seats >= tier.minSeats())
                .mapToInt(VolumeDiscount::percent)
                .max()
                .orElse(0);
    }

    private BigDecimal round(BigDecimal amount) {
        return scale(amount.divide(roundTo, 0, RoundingMode.HALF_UP).multiply(roundTo));
    }

    private static BigDecimal scale(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    /** {@code "10:10,25:15"} → seats from 10 get 10% off, from 25 get 15% off. */
    private static List<VolumeDiscount> parseDiscounts(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::strip)
                .filter(entry -> !entry.isEmpty())
                .map(
                        entry -> {
                            String[] parts = entry.split(":");
                            if (parts.length != 2) {
                                throw new IllegalArgumentException(
                                        "Volume discount must look like <minSeats>:<percent>: "
                                                + entry);
                            }
                            int percent = Integer.parseInt(parts[1].strip());
                            if (percent < 0 || percent >= 100) {
                                throw new IllegalArgumentException(
                                        "Volume discount percent must be between 0 and 99: "
                                                + entry);
                            }
                            return new VolumeDiscount(Integer.parseInt(parts[0].strip()), percent);
                        })
                .sorted(Comparator.comparingInt(VolumeDiscount::minSeats))
                .toList();
    }

    public record VolumeDiscount(int minSeats, int percent) {}

    public record Quote(
            BigDecimal listPricePerSeat,
            int discountPercent,
            BigDecimal pricePerSeat,
            BigDecimal subtotal,
            BigDecimal discountAmount,
            BigDecimal totalAmount) {}
}
