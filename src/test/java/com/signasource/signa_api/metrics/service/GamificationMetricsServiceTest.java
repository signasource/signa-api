package com.signasource.signa_api.metrics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.gamification.entity.ChallengeType;
import com.signasource.signa_api.metrics.dto.GamificationMetricsResponse;
import com.signasource.signa_api.metrics.repository.GamificationMetricsRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GamificationMetricsServiceTest {

    @Mock private GamificationMetricsRepository repository;
    @InjectMocks private GamificationMetricsService service;

    private final MetricsRange range =
            MetricsRange.of(
                    Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-08T00:00:00Z"));

    @BeforeEach
    void stubs() {
        when(repository.sumXpBetween(any(), any())).thenReturn(5000L);
        when(repository.dailyXp(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {LocalDate.of(2026, 11, 2), 700L}));
        when(repository.averageCurrentStreak()).thenReturn(12.34);
        when(repository.countStreakOver(anyInt())).thenReturn(8L);
        when(repository.countActiveStreakers()).thenReturn(20L);
        when(repository.countUnlockedBetween(any(), any())).thenReturn(15L);
        when(repository.dailyUnlocked(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {LocalDate.of(2026, 11, 3), 4L}));
        when(repository.topUnlockedAchievements())
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {"Racha 10", 30L}, new Object[] {"XP 1000", 20L}));
        when(repository.challengeClaims(any(), any()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {ChallengeType.DAILY, 100L, 60L},
                                new Object[] {ChallengeType.WEEKLY, 10L, 8L}));
        when(repository.countGiftsSentBetween(any(), any())).thenReturn(40L);
        when(repository.countGiftsClaimedBetween(any(), any())).thenReturn(30L);
        when(repository.sumGemsSpentBetween(any(), any())).thenReturn(1200L);
        when(repository.topShopItems(any(), any()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {"Escudo", 10L, 500L},
                                new Object[] {"Vida", 5L, 100L}));
    }

    @Test
    void assemblesXpAndStreaks() {
        GamificationMetricsResponse r = service.getGamificationMetrics(range);

        assertEquals(5000L, r.xp().total());
        assertEquals(1, r.xp().daily().size());
        assertEquals(12.3, r.streaks().averageCurrentStreak());
        assertEquals(8L, r.streaks().usersOverSevenDays());
        assertEquals(20L, r.streaks().activeStreakers());
    }

    @Test
    void assemblesAchievementsAndChallengeClaimRates() {
        GamificationMetricsResponse r = service.getGamificationMetrics(range);

        assertEquals(15L, r.achievements().unlocked());
        assertEquals("Racha 10", r.achievements().topUnlocked().get(0).name());
        assertEquals(2, r.challenges().size());
        assertEquals("DAILY", r.challenges().get(0).type());
        assertEquals(60.0, r.challenges().get(0).claimRatePercent());
        assertEquals(80.0, r.challenges().get(1).claimRatePercent());
    }

    @Test
    void assemblesGiftsAndShop() {
        GamificationMetricsResponse r = service.getGamificationMetrics(range);

        assertEquals(40L, r.gifts().sent());
        assertEquals(30L, r.gifts().claimed());
        assertEquals(1200L, r.shop().totalGemsSpent());
        assertEquals("Escudo", r.shop().topItems().get(0).title());
        assertEquals(500L, r.shop().topItems().get(0).gemsSpent());
    }

    @Test
    void handlesNoStreaksWithoutNullPointer() {
        when(repository.averageCurrentStreak()).thenReturn(null);

        GamificationMetricsResponse r = service.getGamificationMetrics(range);

        assertEquals(0.0, r.streaks().averageCurrentStreak());
    }
}
