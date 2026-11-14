package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.common.ArgentinaTime;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs every day at 00:00 ART. For each user whose active streak was not extended yesterday, burns
 * one streak shield (keeping the streak alive) or resets the streak to 0 if no shields remain.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DailyStreakResetScheduler {

    private final UserStatsRepository userStatsRepository;

    @Scheduled(cron = "0 0 0 * * *", zone = "America/Argentina/Buenos_Aires")
    @Transactional
    public void processExpiredStreaks() {
        LocalDate yesterday = ArgentinaTime.today().minusDays(1);
        List<UserStats> at_risk = userStatsRepository.findAllWithActiveStreakBefore(yesterday);
        log.info("Daily streak reset: {} users missed yesterday ({})", at_risk.size(), yesterday);

        for (UserStats stats : at_risk) {
            if (stats.getStreakShields() > 0) {
                stats.setStreakShields(stats.getStreakShields() - 1);
                stats.setLastStreakDate(yesterday);
            } else {
                stats.setCurrentStreak(0);
            }
            stats.setUpdatedAt(Instant.now());
        }

        if (!at_risk.isEmpty()) {
            userStatsRepository.saveAll(at_risk);
        }
        log.info("Daily streak reset complete");
    }
}
