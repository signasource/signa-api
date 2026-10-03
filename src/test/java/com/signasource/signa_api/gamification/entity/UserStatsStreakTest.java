package com.signasource.signa_api.gamification.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
