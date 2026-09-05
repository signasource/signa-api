package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.gamification.dto.DailyXpResponse;
import com.signasource.signa_api.gamification.dto.UserStatsResponse;
import com.signasource.signa_api.gamification.entity.LivesMode;
import com.signasource.signa_api.gamification.entity.UserDailyXp;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserDailyXpRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.users.entity.User;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserStatsServiceTest {

    @Mock private UserDailyXpRepository userDailyXpRepository;
    @Mock private UserStatsRepository userStatsRepository;

    @InjectMocks private UserStatsService userStatsService;

    private User user;
    private LocalDate today;
    private LocalDate monday;

    @BeforeEach
    void setUp() {
        user = User.builder().id(UUID.randomUUID()).build();
        today = LocalDate.now(ZoneOffset.UTC);
        monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    @Test
    void shouldReturnDaysFromMondayToToday() {
        when(userDailyXpRepository.findByUserAndXpDateBetween(eq(user), any(), any()))
                .thenReturn(List.of());

        List<DailyXpResponse> result = userStatsService.getWeeklyXpBreakdown(user);

        int expectedDays = (int) (today.toEpochDay() - monday.toEpochDay() + 1);
        assertEquals(expectedDays, result.size());
        assertEquals(monday, result.get(0).date());
        assertEquals(today, result.get(result.size() - 1).date());
    }

    @Test
    void shouldReturnZeroXp_ForDaysWithNoActivity() {
        when(userDailyXpRepository.findByUserAndXpDateBetween(eq(user), any(), any()))
                .thenReturn(List.of());

        List<DailyXpResponse> result = userStatsService.getWeeklyXpBreakdown(user);

        result.forEach(r -> assertEquals(0, r.xpEarned()));
    }

    @Test
    void shouldReturnCorrectXp_ForDayWithRecord() {
        UserDailyXp todayRecord =
                UserDailyXp.builder().user(user).xpDate(today).xpEarned(90).build();
        when(userDailyXpRepository.findByUserAndXpDateBetween(eq(user), any(), any()))
                .thenReturn(List.of(todayRecord));

        List<DailyXpResponse> result = userStatsService.getWeeklyXpBreakdown(user);

        DailyXpResponse last = result.get(result.size() - 1);
        assertEquals(today, last.date());
        assertEquals(90, last.xpEarned());
    }

    @Test
    void shouldReturnStats_WhenUserHasStats() {
        UserStats stats =
                UserStats.builder()
                        .user(user)
                        .totalXp(1500)
                        .weeklyXp(200)
                        .currentStreak(7)
                        .longestStreak(14)
                        .gems(50)
                        .streakShields(1)
                        .learnedSignsCount(30)
                        .currentLives(3)
                        .livesMode(LivesMode.LIMITED)
                        .updatedAt(Instant.now())
                        .build();
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));

        UserStatsResponse result = userStatsService.getStats(user);

        assertEquals(1500, result.totalXp());
        assertEquals(200, result.weeklyXp());
        assertEquals(7, result.currentStreak());
        assertEquals(14, result.longestStreak());
        assertEquals(50, result.gems());
        assertEquals(1, result.streakShields());
        assertEquals(30, result.learnedSignsCount());
        assertEquals(3, result.currentLives());
        assertEquals(LivesMode.LIMITED, result.livesMode());
    }

    @Test
    void shouldCreateAndReturnDefaultStats_WhenUserHasNoStats() {
        UserStats created = UserStats.builder().user(user).updatedAt(Instant.now()).build();
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.empty());
        when(userStatsRepository.save(any(UserStats.class))).thenReturn(created);

        UserStatsResponse result = userStatsService.getStats(user);

        verify(userStatsRepository).save(any(UserStats.class));
        assertEquals(0, result.totalXp());
        assertEquals(LivesMode.LIMITED, result.livesMode());
    }

    @Test
    void shouldDefaultCurrentLivesToMaxLives_WhenCurrentLivesIsNull() {
        UserStats stats =
                UserStats.builder()
                        .user(user)
                        .livesMode(LivesMode.LIMITED)
                        .updatedAt(Instant.now())
                        .build();
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));

        UserStatsResponse result = userStatsService.getStats(user);

        assertEquals(UserStats.MAX_LIVES, result.currentLives());
    }

    @Test
    void shouldFillGapsWithZero_WhenOnlySomeDaysHaveRecords() {
        UserDailyXp mondayRecord =
                UserDailyXp.builder().user(user).xpDate(monday).xpEarned(50).build();
        when(userDailyXpRepository.findByUserAndXpDateBetween(eq(user), any(), any()))
                .thenReturn(List.of(mondayRecord));

        List<DailyXpResponse> result = userStatsService.getWeeklyXpBreakdown(user);

        assertEquals(50, result.get(0).xpEarned());
        for (int i = 1; i < result.size(); i++) {
            assertEquals(0, result.get(i).xpEarned());
        }
    }
}
