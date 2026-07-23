package com.signasource.signa_api.gamification.entity;

import com.signasource.signa_api.users.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
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
        name = "user_challenges",
        uniqueConstraints =
                @UniqueConstraint(columnNames = {"user_id", "challenge_id", "period_start"}))
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Challenge challenge;

    @Column(nullable = false)
    private LocalDate periodStart;

    @Column(nullable = false)
    private LocalDate periodEnd;

    @Column(nullable = false)
    @Builder.Default
    private int currentProgress = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean completed = false;

    @Column private Instant completedAt;

    @Column private Instant rewardClaimedAt;

    @Column(nullable = false)
    private Instant startedAt;

    public boolean isRewardPending() {
        return completed && rewardClaimedAt == null;
    }

    public boolean isPeriodActive() {
        LocalDate today = LocalDate.now();
        return !today.isBefore(periodStart) && !today.isAfter(periodEnd);
    }
}
