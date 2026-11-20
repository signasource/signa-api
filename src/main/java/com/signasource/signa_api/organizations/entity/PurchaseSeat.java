package com.signasource.signa_api.organizations.entity;

import com.signasource.signa_api.users.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Entity
@Table(
        name = "purchase_seats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"purchase_id", "email"}))
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseSeat {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private CoursePurchase purchase;

    @Column(nullable = false, length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SeatStatus status = SeatStatus.PENDING_PAYMENT;

    @Column(unique = true, length = 64)
    @ToString.Exclude
    private String invitationToken;

    @Column private Instant invitedAt;

    @Column private Instant lastReminderAt;

    @Column private Instant autoReminderSentAt;

    @Column private Instant activatedAt;

    @Column private Instant accessExpiresAt;

    @Column private Instant revokedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    public SeatStatus statusAt(Instant now) {
        if (status == SeatStatus.INVITED) {
            Instant deadline = purchase.getInvitationDeadline();
            return deadline != null && !now.isBefore(deadline)
                    ? SeatStatus.INVITATION_EXPIRED
                    : SeatStatus.INVITED;
        }
        if (status == SeatStatus.ACTIVE
                && accessExpiresAt != null
                && !now.isBefore(accessExpiresAt)) {
            return SeatStatus.ACCESS_EXPIRED;
        }
        return status;
    }

    public boolean isLive(Instant now) {
        SeatStatus current = statusAt(now);
        return current == SeatStatus.PENDING_PAYMENT
                || current == SeatStatus.INVITED
                || current == SeatStatus.ACTIVE;
    }
}
