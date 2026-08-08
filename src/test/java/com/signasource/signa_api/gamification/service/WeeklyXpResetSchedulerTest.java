package com.signasource.signa_api.gamification.service;

import static org.mockito.Mockito.verify;

import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeeklyXpResetSchedulerTest {

    @Mock private UserStatsRepository userStatsRepository;

    @InjectMocks private WeeklyXpResetScheduler weeklyXpResetScheduler;

    @Test
    void resetWeeklyXp_InvokesBulkReset() {
        weeklyXpResetScheduler.resetWeeklyXp();

        verify(userStatsRepository).resetAllWeeklyXp();
    }
}
