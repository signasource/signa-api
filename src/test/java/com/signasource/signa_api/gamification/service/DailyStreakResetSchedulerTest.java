package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.common.ArgentinaTime;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DailyStreakResetSchedulerTest {

    @Mock private UserStatsRepository userStatsRepository;

    @InjectMocks private DailyStreakResetScheduler scheduler;

    // Dates computed at test-run time so they always match ArgentinaTime.today() inside the
    // scheduler.
    private static final LocalDate YESTERDAY = ArgentinaTime.today().minusDays(1);

    private UserStats statsWithStreak(LocalDate lastStreakDate, int streak, int shields) {
        return UserStats.builder()
                .currentStreak(streak)
                .longestStreak(streak)
                .lastStreakDate(lastStreakDate)
                .streakShields(shields)
                .build();
    }

    @Test
    void shouldBurnOneShieldForOneMissedDay() {
        UserStats stats = statsWithStreak(YESTERDAY.minusDays(1), 5, 2);
        when(userStatsRepository.findAllWithActiveStreakBefore(any())).thenReturn(List.of(stats));

        scheduler.processExpiredStreaks();

        assertEquals(5, stats.getCurrentStreak());
        assertEquals(1, stats.getStreakShields());
        assertEquals(YESTERDAY, stats.getLastStreakDate());
        assertTrue(stats.isStreakShieldPending());
        verify(userStatsRepository).saveAll(anyList());
    }

    @Test
    void shouldBurnTwoShieldsForTwoMissedDays() {
        UserStats stats = statsWithStreak(YESTERDAY.minusDays(2), 7, 3);
        when(userStatsRepository.findAllWithActiveStreakBefore(any())).thenReturn(List.of(stats));

        scheduler.processExpiredStreaks();

        assertEquals(7, stats.getCurrentStreak());
        assertEquals(1, stats.getStreakShields());
        assertEquals(YESTERDAY, stats.getLastStreakDate());
        assertTrue(stats.isStreakShieldPending());
    }

    @Test
    void shouldResetStreakWhenNotEnoughShields() {
        UserStats stats = statsWithStreak(YESTERDAY.minusDays(2), 7, 1);
        when(userStatsRepository.findAllWithActiveStreakBefore(any())).thenReturn(List.of(stats));

        scheduler.processExpiredStreaks();

        assertEquals(0, stats.getCurrentStreak());
        assertEquals(1, stats.getStreakShields());
        assertFalse(stats.isStreakShieldPending());
    }

    @Test
    void shouldResetStreakWhenNoShields() {
        UserStats stats = statsWithStreak(YESTERDAY.minusDays(1), 3, 0);
        when(userStatsRepository.findAllWithActiveStreakBefore(any())).thenReturn(List.of(stats));

        scheduler.processExpiredStreaks();

        assertEquals(0, stats.getCurrentStreak());
        assertFalse(stats.isStreakShieldPending());
    }

    @Test
    void shouldNotSaveWhenNoUsersAtRisk() {
        when(userStatsRepository.findAllWithActiveStreakBefore(any())).thenReturn(List.of());

        scheduler.processExpiredStreaks();

        verify(userStatsRepository, never()).saveAll(anyList());
    }

    @Test
    void shieldBehaviorMatchesRegisterStreakActivityForOneMissedDay() {
        // Scheduler and registerStreakActivity must agree: 1 missed day with 1 shield → streak
        // intact.
        UserStats viaScheduler = statsWithStreak(YESTERDAY.minusDays(1), 5, 1);
        when(userStatsRepository.findAllWithActiveStreakBefore(any()))
                .thenReturn(List.of(viaScheduler));
        scheduler.processExpiredStreaks();

        UserStats viaMethod =
                UserStats.builder()
                        .currentStreak(5)
                        .longestStreak(5)
                        .lastStreakDate(YESTERDAY.minusDays(1))
                        .streakShields(1)
                        .build();
        viaMethod.registerStreakActivity(ArgentinaTime.today());

        assertEquals(viaMethod.getCurrentStreak(), viaScheduler.getCurrentStreak());
        assertEquals(viaMethod.getStreakShields(), viaScheduler.getStreakShields());
    }
}
