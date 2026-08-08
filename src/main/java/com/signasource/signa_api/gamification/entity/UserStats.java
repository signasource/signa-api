package com.signasource.signa_api.gamification.entity;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Entity
@Table(name = "user_stats")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStats {

    private static final int MAX_LIVES = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    @ToString.Exclude
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private int currentStreak = 0;

    @Column(nullable = false)
    @Builder.Default
    private int longestStreak = 0;

    @Column(nullable = false)
    @Builder.Default
    private long totalXp = 0;

    @Column(nullable = false)
    @Builder.Default
    private int weeklyXp = 0;

    @Column(nullable = false)
    @Builder.Default
    private int gems = 0;

    @Column(nullable = false)
    @Builder.Default
    private int streakShields = 0;

    @Column(nullable = false)
    @Builder.Default
    private double xpMultiplier = 1.0;

    @Column private Instant xpMultiplierExpiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LivesMode livesMode = LivesMode.LIMITED;

    @Column private Integer currentLives;

    @Column private Instant nextLifeAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public double getEffectiveXpMultiplier() {
        if (xpMultiplierExpiresAt != null && xpMultiplierExpiresAt.isAfter(Instant.now())) {
            return xpMultiplier;
        }
        return 1.0;
    }

    public boolean hasActiveXpMultiplier() {
        return getEffectiveXpMultiplier() > 1.0;
    }

    public boolean isLivesRegenerating() {
        return livesMode == LivesMode.LIMITED && currentLives != null && currentLives < MAX_LIVES;
    }
}
