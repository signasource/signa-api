package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.AchievementResponse;
import com.signasource.signa_api.gamification.entity.Achievement;
import com.signasource.signa_api.gamification.entity.UserAchievement;
import com.signasource.signa_api.gamification.repository.AchievementRepository;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AchievementService {

    private final AchievementRepository achievementRepository;

    @Transactional(readOnly = true)
    public List<AchievementResponse> getAchievements(User user, Boolean unlocked, Boolean active) {
        ensureEnabled(user);

        return achievementRepository.findAllWithUserAchievement(user).stream()
                .map(
                        row ->
                                AchievementResponse.from(
                                        (Achievement) row[0], (UserAchievement) row[1]))
                .filter(r -> unlocked == null || r.earned() == unlocked)
                .filter(r -> active == null || r.active() == active)
                .toList();
    }

    @Transactional(readOnly = true)
    public AchievementResponse getAchievementById(UUID achievementId, User user) {
        ensureEnabled(user);

        Object[] row =
                achievementRepository
                        .findByIdWithUserAchievement(achievementId, user)
                        .orElseThrow(() -> new NotFoundException("Achievement not found"));

        return AchievementResponse.from((Achievement) row[0], (UserAchievement) row[1]);
    }

    private void ensureEnabled(User user) {
        if (!user.isEnabled()) {
            throw new NotFoundException("User not found");
        }
    }
}
