package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.event.XpEarnedEvent;
import com.signasource.signa_api.users.entity.User;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserStatsEventListenerTest {

    @Mock private UserStatsRepository userStatsRepository;

    @InjectMocks private UserStatsEventListener userStatsEventListener;

    private UUID userId;
    private User mockUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        mockUser = new User();
        mockUser.setId(userId);
    }

    private static Instant startOfCurrentWeek() {
        return ZonedDateTime.now(ZoneOffset.UTC)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .toLocalDate()
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();
    }

    @Test
    void handleXpEarnedEvent_CreatesStats_WhenUserHasNone() {
        when(userStatsRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(userStatsRepository.save(any(UserStats.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userStatsEventListener.handleXpEarnedEvent(new XpEarnedEvent(this, mockUser, 50));

        ArgumentCaptor<UserStats> captor = ArgumentCaptor.forClass(UserStats.class);
        verify(userStatsRepository).save(captor.capture());
        UserStats saved = captor.getValue();
        assertEquals(50L, saved.getTotalXp());
        assertEquals(50, saved.getWeeklyXp());
        assertEquals(mockUser, saved.getUser());
    }

    @Test
    void handleXpEarnedEvent_AccumulatesXp_WithinSameWeek() {
        UserStats stats =
                UserStats.builder()
                        .user(mockUser)
                        .totalXp(100)
                        .weeklyXp(30)
                        .updatedAt(startOfCurrentWeek())
                        .build();
        when(userStatsRepository.findByUserId(userId)).thenReturn(Optional.of(stats));
        when(userStatsRepository.save(any(UserStats.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userStatsEventListener.handleXpEarnedEvent(new XpEarnedEvent(this, mockUser, 20));

        assertEquals(120L, stats.getTotalXp());
        assertEquals(50, stats.getWeeklyXp());
    }

    @Test
    void handleXpEarnedEvent_ResetsWeeklyXp_WhenLastUpdateWasBeforeCurrentWeek() {
        UserStats stats =
                UserStats.builder()
                        .user(mockUser)
                        .totalXp(200)
                        .weeklyXp(80)
                        .updatedAt(startOfCurrentWeek().minusSeconds(1))
                        .build();
        when(userStatsRepository.findByUserId(userId)).thenReturn(Optional.of(stats));
        when(userStatsRepository.save(any(UserStats.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userStatsEventListener.handleXpEarnedEvent(new XpEarnedEvent(this, mockUser, 10));

        assertEquals(210L, stats.getTotalXp());
        assertEquals(10, stats.getWeeklyXp());
    }
}
