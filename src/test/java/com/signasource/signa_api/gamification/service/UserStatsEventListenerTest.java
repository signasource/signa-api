package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.event.XpEarnedEvent;
import com.signasource.signa_api.users.entity.User;
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
        assertNotNull(saved.getUpdatedAt());
    }

    @Test
    void handleXpEarnedEvent_AccumulatesXp() {
        UserStats stats = UserStats.builder().user(mockUser).totalXp(100).weeklyXp(30).build();
        when(userStatsRepository.findByUserId(userId)).thenReturn(Optional.of(stats));
        when(userStatsRepository.save(any(UserStats.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userStatsEventListener.handleXpEarnedEvent(new XpEarnedEvent(this, mockUser, 20));

        assertEquals(120L, stats.getTotalXp());
        assertEquals(50, stats.getWeeklyXp());
    }
}
