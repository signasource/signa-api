package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.gamification.dto.DailyXpResponse;
import com.signasource.signa_api.gamification.entity.UserDailyXp;
import com.signasource.signa_api.gamification.repository.UserDailyXpRepository;
import com.signasource.signa_api.users.entity.User;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
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
