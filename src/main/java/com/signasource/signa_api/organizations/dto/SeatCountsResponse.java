package com.signasource.signa_api.organizations.dto;

import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.SeatStatus;
import java.time.Instant;
import java.util.Collection;

public record SeatCountsResponse(
        int pendingPayment,
        int invited,
        int invitationExpired,
        int active,
        int accessExpired,
        int revoked) {

    public static SeatCountsResponse of(Collection<PurchaseSeat> seats, Instant now) {
        int[] counts = new int[SeatStatus.values().length];
        seats.forEach(seat -> counts[seat.statusAt(now).ordinal()]++);
        return new SeatCountsResponse(
                counts[SeatStatus.PENDING_PAYMENT.ordinal()],
                counts[SeatStatus.INVITED.ordinal()],
                counts[SeatStatus.INVITATION_EXPIRED.ordinal()],
                counts[SeatStatus.ACTIVE.ordinal()],
                counts[SeatStatus.ACCESS_EXPIRED.ordinal()],
                counts[SeatStatus.REVOKED.ordinal()]);
    }
}
