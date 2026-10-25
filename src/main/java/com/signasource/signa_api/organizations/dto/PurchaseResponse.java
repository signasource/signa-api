package com.signasource.signa_api.organizations.dto;

import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

public record PurchaseResponse(
        UUID id,
        UUID courseId,
        String courseName,
        int seats,
        int durationDays,
        int signCount,
        BigDecimal listPricePerSeat,
        int discountPercent,
        BigDecimal pricePerSeat,
        BigDecimal totalAmount,
        String currency,
        PurchaseStatus status,
        Instant createdAt,
        Instant paidAt,
        Instant invitationDeadline,
        Instant cancelledAt,
        SeatCountsResponse seatCounts) {

    public static PurchaseResponse from(
            CoursePurchase purchase, Collection<PurchaseSeat> seats, Instant now) {
        return new PurchaseResponse(
                purchase.getId(),
                purchase.getCourse().getId(),
                purchase.getCourse().getName(),
                purchase.getSeats(),
                purchase.getDurationDays(),
                purchase.getSignCount(),
                purchase.getListPricePerSeat(),
                purchase.getDiscountPercent(),
                purchase.getPricePerSeat(),
                purchase.getTotalAmount(),
                purchase.getCurrency(),
                purchase.getStatus(),
                purchase.getCreatedAt(),
                purchase.getPaidAt(),
                purchase.getInvitationDeadline(),
                purchase.getCancelledAt(),
                SeatCountsResponse.of(seats, now));
    }
}
