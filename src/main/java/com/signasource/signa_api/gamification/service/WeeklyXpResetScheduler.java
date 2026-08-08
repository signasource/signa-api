package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class WeeklyXpResetScheduler {

    private final UserStatsRepository userStatsRepository;

    // Runs every Monday at 00:00 UTC; the fixed zone keeps the reset boundary stable across DST.
    @Scheduled(cron = "0 0 0 * * MON", zone = "UTC")
    @Transactional
    public void resetWeeklyXp() {
        userStatsRepository.resetAllWeeklyXp();
    }
}
