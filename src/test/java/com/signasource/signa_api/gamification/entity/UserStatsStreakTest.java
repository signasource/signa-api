package com.signasource.signa_api.gamification.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signasource.signa_api.common.ArgentinaTime;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class UserStatsStreakTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    @Test
    void firstActivityStartsTheStreak() {
        UserStats stats = UserStats.builder().build();

        assertTrue(stats.registerStreakActivity(DAY));

        assertEquals(1, stats.getCurrentStreak());
        assertEquals(1, stats.getLongestStreak());
        assertEquals(DAY, stats.getLastStreakDate());
    }

    @Test
    void secondActivityTheSameDayDoesNothing() {
        UserStats stats = UserStats.builder().build();
        stats.registerStreakActivity(DAY);

        assertFalse(stats.registerStreakActivity(DAY));

        assertEquals(1, stats.getCurrentStreak());
    }

    @Test
    void consecutiveDayExtendsTheStreak() {
        UserStats stats = UserStats.builder().build();
        stats.registerStreakActivity(DAY);

        assertTrue(stats.registerStreakActivity(DAY.plusDays(1)));

        assertEquals(2, stats.getCurrentStreak());
        assertEquals(2, stats.getLongestStreak());
    }

    @Test
    void missedDayBurnsAShieldAndKeepsTheStreak() {
        UserStats stats = UserStats.builder().streakShields(1).build();
        stats.registerStreakActivity(DAY);

        stats.registerStreakActivity(DAY.plusDays(2));

        assertEquals(2, stats.getCurrentStreak());
        assertEquals(0, stats.getStreakShields());
    }

    @Test
    void missedDaysWithoutEnoughShieldsRestartTheStreak() {
        UserStats stats = UserStats.builder().streakShields(1).build();
        stats.registerStreakActivity(DAY);
        stats.registerStreakActivity(DAY.plusDays(1));

        stats.registerStreakActivity(DAY.plusDays(4));

        assertEquals(1, stats.getCurrentStreak());
        assertEquals(2, stats.getLongestStreak());
        assertEquals(1, stats.getStreakShields());
    }

    @Test
    void streakDayRollsOverAtMidnightInArgentina() {
        // 02:59Z is still 23:59 of the previous day in Córdoba (UTC-3).
        assertEquals(
                LocalDate.of(2026, 10, 3),
                ArgentinaTime.dayOf(Instant.parse("2026-10-04T02:59:59Z")));
        assertEquals(
                LocalDate.of(2026, 10, 4),
                ArgentinaTime.dayOf(Instant.parse("2026-10-04T03:00:00Z")));
    }

    @Test
    void lateEveningActivityCountsAsTheSameLocalDay() {
        UserStats stats = UserStats.builder().build();

        stats.registerStreakActivity(ArgentinaTime.dayOf(Instant.parse("2026-10-03T15:00:00Z")));
        boolean advanced =
                stats.registerStreakActivity(
                        ArgentinaTime.dayOf(Instant.parse("2026-10-04T01:30:00Z")));

        // 12:00 and 22:30 local on Oct 3: one streak day, not two.
        assertFalse(advanced);
        assertEquals(1, stats.getCurrentStreak());
    }
}
