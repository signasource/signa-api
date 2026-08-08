package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.event.XpEarnedEvent;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserStatsEventListener {

    private final UserStatsRepository userStatsRepository;

    // Synchronous, same-transaction listener (unlike the AFTER_COMMIT push notifications): XP and
    // course progress must commit atomically so a rollback never leaves XP awarded without
    // progress.
    @EventListener
    @Transactional
    public void handleXpEarnedEvent(XpEarnedEvent event) {
        int xpToAdd = event.getXpAmount();

        UserStats stats =
                userStatsRepository
                        .findByUserId(event.getUser().getId())
                        .orElseGet(() -> UserStats.builder().user(event.getUser()).build());

        stats.setTotalXp(stats.getTotalXp() + xpToAdd);
        stats.setWeeklyXp(stats.getWeeklyXp() + xpToAdd);
        stats.setUpdatedAt(Instant.now());

        userStatsRepository.save(stats);
    }
}
