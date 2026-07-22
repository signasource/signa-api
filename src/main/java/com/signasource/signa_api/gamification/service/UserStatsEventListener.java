package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.event.XpEarnedEvent;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserStatsEventListener {

    private final UserStatsRepository userStatsRepository;
    private final UserRepository userRepository;

    @EventListener
    @Transactional
    public void handleXpEarnedEvent(XpEarnedEvent event) {
        UUID userId = event.getUserId();
        Integer xpToAdd = event.getXpAmount();

        UserStats stats =
                userStatsRepository
                        .findByUserId(userId)
                        .orElseGet(
                                () -> {
                                    User userProxy = userRepository.getReferenceById(userId);
                                    return UserStats.builder()
                                            .user(userProxy)
                                            .totalXp(0)
                                            .weeklyXp(0)
                                            .build();
                                });

        stats.setTotalXp(stats.getTotalXp() + xpToAdd);
        stats.setWeeklyXp(stats.getWeeklyXp() + xpToAdd);

        userStatsRepository.save(stats);
    }
}
