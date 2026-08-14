package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.gamification.entity.UserLearnedSign;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserLearnedSignRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.event.SignsLearnedEvent;
import com.signasource.signa_api.learning.event.XpEarnedEvent;
import com.signasource.signa_api.users.entity.User;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
public class UserStatsEventListener {

    private final UserStatsRepository userStatsRepository;
    private final UserLearnedSignRepository userLearnedSignRepository;

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

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSignsLearnedEvent(SignsLearnedEvent event) {
        User user = event.getUser();
        UUID courseVersionId = event.getCourseVersion().getId();

        List<String> newInCourse =
                event.getSigns().stream()
                        .distinct()
                        .filter(
                                sign ->
                                        !userLearnedSignRepository
                                                .existsByUserIdAndSignAndCourseVersionId(
                                                        user.getId(), sign, courseVersionId))
                        .toList();

        if (newInCourse.isEmpty()) {
            return;
        }

        List<String> newGlobally =
                newInCourse.stream()
                        .filter(
                                sign ->
                                        !userLearnedSignRepository.existsByUserIdAndSign(
                                                user.getId(), sign))
                        .toList();

        Instant now = Instant.now();
        newInCourse.forEach(
                sign ->
                        userLearnedSignRepository.save(
                                UserLearnedSign.builder()
                                        .user(user)
                                        .sign(sign)
                                        .courseVersion(event.getCourseVersion())
                                        .learnedAt(now)
                                        .build()));

        if (newGlobally.isEmpty()) {
            return;
        }

        UserStats stats =
                userStatsRepository
                        .findByUserId(user.getId())
                        .orElseGet(() -> UserStats.builder().user(user).build());
        stats.setLearnedSignsCount(stats.getLearnedSignsCount() + newGlobally.size());
        stats.setUpdatedAt(now);
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
