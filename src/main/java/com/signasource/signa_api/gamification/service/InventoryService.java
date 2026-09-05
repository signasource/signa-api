package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.UserInventoryResponse;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final UserStatsRepository userStatsRepository;

    @Transactional
    public UserInventoryResponse getInventory(User user) {
        ensureEnabled(user);

        UserStats stats =
                userStatsRepository
                        .findByUser(user)
                        .orElseGet(
                                () ->
                                        userStatsRepository.save(
                                                UserStats.builder()
                                                        .user(user)
                                                        .updatedAt(Instant.now())
                                                        .build()));

        return UserInventoryResponse.from(stats);
    }

    private void ensureEnabled(User user) {
        if (!user.isEnabled()) {
            throw new NotFoundException("User not found");
        }
    }
}
