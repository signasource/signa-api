package com.signasource.signa_api.gamification.entity;

import com.signasource.signa_api.common.ArgentinaTime;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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

    public static final int MAX_LIVES = 5;

    /** One life comes back every this many minutes while the bar is not full. */
    public static final int LIFE_REGEN_MINUTES = 30;

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

    /** Streak day ({@link ArgentinaTime}) of the last activity that counted towards the streak. */
    @Column private LocalDate lastStreakDate;

    /** Streak day for which the app already showed the "streak day added" celebration. */
    @Column private LocalDate streakCelebratedDate;

    /** Last Argentina date on which the user made any authenticated request (app-open proxy). */
    @Column private LocalDate lastSeenDate;

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

    @Column @Builder.Default private Integer currentLives = MAX_LIVES;

    @Column private Instant nextLifeAt;

    @Column private Instant unlimitedLivesExpiresAt;

    @Column(nullable = false)
    @Builder.Default
    private int learnedSignsCount = 0;

    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * When the user last claimed the mistake-review XP bonus. A new claim requires a mistake
     * resolved after this moment, so the bonus can't be farmed (see PracticeService).
     */
    @Column private Instant lastMistakeReviewAt;

    /** Rank at the end of the previous weekly cycle. Null until the first reset runs. */
    @Column private Integer previousWeeklyRank;

    /**
     * Set by the nightly scheduler when it burns a shield to keep the streak alive. Cleared once
     * the app acknowledges it.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean streakShieldPending = false;

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

    /**
     * A null expiry means the infinite-lives mode was granted without a time limit (e.g. a
     * permanent grant), so it never lapses on its own.
     */
    public boolean hasActiveUnlimitedLives() {
        if (livesMode != LivesMode.INFINITE) {
            return false;
        }
        return unlimitedLivesExpiresAt == null || unlimitedLivesExpiresAt.isAfter(Instant.now());
    }

    /** Lives and next-regeneration instant as they stand at a given moment. */
    public record LifeState(int lives, Instant nextLifeAt) {}

    /**
     * Lives derived from {@code currentLives}/{@code nextLifeAt} at {@code now}, without mutating
     * anything: one life per elapsed {@link #LIFE_REGEN_MINUTES} since {@code nextLifeAt}. A
     * missing {@code currentLives} means a full bar; a missing {@code nextLifeAt} on a non-full bar
     * (rows from before regeneration existed) starts the timer at {@code now}.
     */
    public LifeState lifeStateAt(Instant now) {
        int lives = currentLives == null ? MAX_LIVES : Math.min(currentLives, MAX_LIVES);
        if (lives >= MAX_LIVES) {
            return new LifeState(MAX_LIVES, null);
        }
        Instant next =
                nextLifeAt == null ? now.plus(LIFE_REGEN_MINUTES, ChronoUnit.MINUTES) : nextLifeAt;
        while (lives < MAX_LIVES && !next.isAfter(now)) {
            lives++;
            next = next.plus(LIFE_REGEN_MINUTES, ChronoUnit.MINUTES);
        }
        return new LifeState(lives, lives >= MAX_LIVES ? null : next);
    }

    /** Persists the regeneration that has elapsed; also drops an expired unlimited-lives mode. */
    public void regenerateLives(Instant now) {
        if (livesMode == LivesMode.INFINITE && !hasActiveUnlimitedLives()) {
            livesMode = LivesMode.LIMITED;
        }
        if (livesMode != LivesMode.LIMITED) {
            return;
        }
        LifeState state = lifeStateAt(now);
        currentLives = state.lives();
        nextLifeAt = state.nextLifeAt();
    }

    public LivesMode getEffectiveLivesMode() {
        return hasActiveUnlimitedLives() || livesMode != LivesMode.INFINITE
                ? livesMode
                : LivesMode.LIMITED;
    }

    /**
     * Deducts one life for a wrong answer. No-op when lives are unlimited or already at zero.
     *
     * @return true if a life was actually deducted
     */
    public boolean loseLife() {
        Instant now = Instant.now();
        regenerateLives(now);
        if (livesMode != LivesMode.LIMITED || currentLives <= 0) {
            return false;
        }
        boolean wasFull = currentLives >= MAX_LIVES;
        currentLives = currentLives - 1;
        if (wasFull || nextLifeAt == null) {
            nextLifeAt = now.plus(LIFE_REGEN_MINUTES, ChronoUnit.MINUTES);
        }
        return true;
    }

    /**
     * Counts {@code today} as a day of activity. Consecutive days extend the streak; each missed
     * day burns one streak shield, and without enough shields the streak restarts at 1.
     *
     * @return {@code true} when the streak advanced (first activity of the day)
     */
    public boolean registerStreakActivity(LocalDate today) {
        if (lastStreakDate != null && !today.isAfter(lastStreakDate)) {
            return false;
        }
        if (lastStreakDate == null) {
            currentStreak = 1;
        } else {
            long missedDays = ChronoUnit.DAYS.between(lastStreakDate, today) - 1;
            if (missedDays <= streakShields) {
                streakShields -= (int) missedDays;
                currentStreak += 1;
            } else {
                currentStreak = 1;
            }
        }
        longestStreak = Math.max(longestStreak, currentStreak);
        lastStreakDate = today;
        return true;
    }

    /** Whether the nightly scheduler used a shield since the app last acknowledged it. */
    public boolean isStreakShieldPending() {
        return streakShieldPending;
    }

    /**
     * Whether {@code today} added a streak day that was not celebrated yet. Only today's day can be
     * pending, so older activity never replays.
     */
    public boolean isStreakDayPending(LocalDate today) {
        return today.equals(lastStreakDate) && !today.equals(streakCelebratedDate);
    }

    /**
     * Marks today's streak day as celebrated.
     *
     * @return {@code true} when something changed
     */
    public boolean markStreakDaySeen(LocalDate today) {
        if (!isStreakDayPending(today)) {
            return false;
        }
        streakCelebratedDate = today;
        return true;
    }
}
