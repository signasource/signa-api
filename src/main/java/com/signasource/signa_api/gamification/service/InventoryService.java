package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.UserInventoryResponse;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.users.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final UserStatsRepository userStatsRepository;

    @Transactional(readOnly = true)
    public UserInventoryResponse getInventory(User user) {
        ensureEnabled(user);

        UserStats stats =
                userStatsRepository
                        .findByUser(user)
                        .orElseThrow(() -> new NotFoundException("User stats not found"));

        return UserInventoryResponse.from(stats);
    }

    private void ensureEnabled(User user) {
        if (!user.isEnabled()) {
            throw new NotFoundException("User not found");
        }
    }
}
