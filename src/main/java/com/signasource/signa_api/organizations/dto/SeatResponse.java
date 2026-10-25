package com.signasource.signa_api.organizations.dto;

import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.SeatStatus;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.util.UUID;

public record SeatResponse(
        UUID id,
        String email,
        SeatStatus status,
        Instant invitedAt,
        Instant lastReminderAt,
        Instant autoReminderSentAt,
        Instant activatedAt,
        Instant accessExpiresAt,
        Instant revokedAt,
        UUID userId,
        String userName,
        boolean canEditEmail,
        Instant nextReminderAvailableAt) {

    public static SeatResponse from(PurchaseSeat seat, Instant now, Instant nextReminderAt) {
        SeatStatus status = seat.statusAt(now);
        User user = seat.getUser();
        return new SeatResponse(
                seat.getId(),
                seat.getEmail(),
                status,
                seat.getInvitedAt(),
                seat.getLastReminderAt(),
                seat.getAutoReminderSentAt(),
                seat.getActivatedAt(),
                seat.getAccessExpiresAt(),
                seat.getRevokedAt(),
                user == null ? null : user.getId(),
                user == null ? null : fullName(user),
                status == SeatStatus.PENDING_PAYMENT || status == SeatStatus.INVITED,
                status == SeatStatus.INVITED ? nextReminderAt : null);
    }

    private static String fullName(User user) {
        String lastName = user.getLastName();
        return lastName == null || lastName.isBlank()
                ? user.getName()
                : user.getName() + " " + lastName;
    }
}
