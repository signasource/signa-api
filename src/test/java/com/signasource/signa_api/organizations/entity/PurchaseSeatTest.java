package com.signasource.signa_api.organizations.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class PurchaseSeatTest {

    private final Instant now = Instant.now();

    private PurchaseSeat seat(SeatStatus status, Instant deadline) {
        CoursePurchase purchase = CoursePurchase.builder().invitationDeadline(deadline).build();
        return PurchaseSeat.builder().purchase(purchase).status(status).build();
    }

    @Test
    void shouldKeepAnInvitationPendingInsideTheWindow() {
        PurchaseSeat seat = seat(SeatStatus.INVITED, now.plus(1, ChronoUnit.DAYS));

        assertEquals(SeatStatus.INVITED, seat.statusAt(now));
        assertTrue(seat.isLive(now));
    }

    @Test
    void shouldExpireAnInvitationOnceTheWindowCloses() {
        PurchaseSeat seat = seat(SeatStatus.INVITED, now);

        assertEquals(SeatStatus.INVITATION_EXPIRED, seat.statusAt(now));
        assertFalse(seat.isLive(now));
    }

    @Test
    void shouldExpireAccessAfterTheContractedDays() {
        PurchaseSeat seat = seat(SeatStatus.ACTIVE, null);
        seat.setAccessExpiresAt(now.minusSeconds(1));

        assertEquals(SeatStatus.ACCESS_EXPIRED, seat.statusAt(now));
        assertFalse(seat.isLive(now));
    }

    @Test
    void shouldKeepActiveAccessUntilItExpires() {
        PurchaseSeat seat = seat(SeatStatus.ACTIVE, null);
        seat.setAccessExpiresAt(now.plus(3, ChronoUnit.DAYS));

        assertEquals(SeatStatus.ACTIVE, seat.statusAt(now));
        assertTrue(seat.isLive(now));
    }

    @Test
    void shouldReportStoredStatesAsTheyAre() {
        assertEquals(
                SeatStatus.PENDING_PAYMENT, seat(SeatStatus.PENDING_PAYMENT, null).statusAt(now));
        assertTrue(seat(SeatStatus.PENDING_PAYMENT, null).isLive(now));
        assertFalse(seat(SeatStatus.REVOKED, null).isLive(now));
        assertFalse(seat(SeatStatus.CANCELLED, null).isLive(now));
    }
}
