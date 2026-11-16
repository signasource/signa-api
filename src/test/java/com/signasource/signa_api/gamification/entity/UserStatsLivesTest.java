package com.signasource.signa_api.gamification.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class UserStatsLivesTest {

    @Test
    void newStatsStartWithFullLivesAndNoTimer() {
        UserStats stats = UserStats.builder().build();

        UserStats.LifeState state = stats.lifeStateAt(Instant.now());

        assertEquals(UserStats.MAX_LIVES, state.lives());
        assertNull(state.nextLifeAt());
    }

    @Test
    void loseLifeStartsTheRegenTimer() {
        UserStats stats = UserStats.builder().build();

        assertTrue(stats.loseLife());

        assertEquals(UserStats.MAX_LIVES - 1, stats.getCurrentLives());
        assertTrue(stats.getNextLifeAt().isAfter(Instant.now()));
    }

    @Test
    void elapsedIntervalsRegenerateLivesAndKeepTheRemainder() {
        Instant now = Instant.now();
        UserStats stats =
                UserStats.builder()
                        .currentLives(1)
                        .nextLifeAt(now.minus(70, ChronoUnit.MINUTES))
                        .build();

        UserStats.LifeState state = stats.lifeStateAt(now);

        assertEquals(4, state.lives());
        assertEquals(now.plus(20, ChronoUnit.MINUTES), state.nextLifeAt());
    }

    @Test
    void regenerationStopsAtFullBar() {
        Instant now = Instant.now();
        UserStats stats =
                UserStats.builder()
                        .currentLives(3)
                        .nextLifeAt(now.minus(10, ChronoUnit.HOURS))
                        .build();

        stats.regenerateLives(now);

        assertEquals(UserStats.MAX_LIVES, stats.getCurrentLives());
        assertNull(stats.getNextLifeAt());
    }
}
