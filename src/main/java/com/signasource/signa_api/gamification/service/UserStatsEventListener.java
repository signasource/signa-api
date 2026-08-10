package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.event.XpEarnedEvent;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserStatsEventListener {

    private final UserStatsRepository userStatsRepository;

    @EventListener
    @Transactional
    public void handleXpEarnedEvent(XpEarnedEvent event) {
        Integer xpToAdd = event.getXpAmount();

        UserStats stats =
                userStatsRepository
                        .findByUserId(event.getUser().getId())
                        .orElseGet(
                                () ->
                                        UserStats.builder()
                                                .user(event.getUser())
                                                .totalXp(0)
                                                .weeklyXp(0)
                                                .build());

        if (isBeforeCurrentWeek(stats.getUpdatedAt())) {
            stats.setWeeklyXp(0);
        }

        stats.setTotalXp(stats.getTotalXp() + xpToAdd);
        stats.setWeeklyXp(stats.getWeeklyXp() + xpToAdd);
        stats.setUpdatedAt(Instant.now());

        userStatsRepository.save(stats);
    }

    private boolean isBeforeCurrentWeek(Instant updatedAt) {
        if (updatedAt == null) {
            return false;
        }

        Instant startOfWeek =
                ZonedDateTime.now(ZoneOffset.UTC)
                        .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        .toLocalDate()
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant();

        return updatedAt.isBefore(startOfWeek);
    }
}
