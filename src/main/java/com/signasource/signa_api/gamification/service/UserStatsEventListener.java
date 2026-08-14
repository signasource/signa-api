package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.gamification.entity.UserDailyXp;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserDailyXpRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.event.XpEarnedEvent;
import com.signasource.signa_api.users.entity.User;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
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
    private final UserDailyXpRepository userDailyXpRepository;

    @EventListener
    @Transactional
    public void handleXpEarnedEvent(XpEarnedEvent event) {
        int xpToAdd = event.getXpAmount();
        User user = event.getUser();

        UserStats stats =
                userStatsRepository
                        .findByUserId(user.getId())
                        .orElseGet(
                                () ->
                                        UserStats.builder()
                                                .user(user)
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

        upsertDailyXp(user, xpToAdd);
    }

    private void upsertDailyXp(User user, int xpToAdd) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        UserDailyXp daily =
                userDailyXpRepository
                        .findByUserAndXpDate(user, today)
                        .orElseGet(() -> UserDailyXp.builder().user(user).xpDate(today).build());
        daily.setXpEarned(daily.getXpEarned() + xpToAdd);
        userDailyXpRepository.save(daily);
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
