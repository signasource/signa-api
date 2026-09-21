package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs every Monday at 00:00 ART (UTC-3).
 * Snapshots each user's current global rank into previousWeeklyRank, then zeroes weeklyXp.
 * Both operations run in a single transaction so the data is always consistent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeeklyResetScheduler {

    private final UserStatsRepository userStatsRepository;

    @Scheduled(cron = "0 0 0 * * MON", zone = "America/Argentina/Buenos_Aires")
    @Transactional
    public void resetWeeklyXp() {
        log.info("Weekly XP reset starting");
        userStatsRepository.computeAndSavePreviousWeeklyRanks();
        userStatsRepository.resetAllWeeklyXp();
        log.info("Weekly XP reset complete");
    }
}
