package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.AchievementResponse;
import com.signasource.signa_api.gamification.entity.Achievement;
import com.signasource.signa_api.gamification.entity.UserAchievement;
import com.signasource.signa_api.gamification.repository.AchievementRepository;
import com.signasource.signa_api.gamification.repository.UserAchievementRepository;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;

    @Transactional(readOnly = true)
    public List<AchievementResponse> getAchievements(User user) {
        ensureEnabled(user);

        List<Achievement> achievements = achievementRepository.findAllByOrderByTitleAsc();

        Map<UUID, UserAchievement> earnedByAchievementId =
                userAchievementRepository.findByUser(user).stream()
                        .collect(
                                Collectors.toMap(
                                        ua -> ua.getAchievement().getId(), Function.identity()));

        return achievements.stream()
                .map(a -> AchievementResponse.from(a, earnedByAchievementId.get(a.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public AchievementResponse getAchievementById(UUID achievementId, User user) {
        ensureEnabled(user);

        Achievement achievement =
                achievementRepository
                        .findById(achievementId)
                        .orElseThrow(() -> new NotFoundException("Achievement not found"));

        UserAchievement userAchievement =
                userAchievementRepository
                        .findByUserAndAchievementId(user, achievementId)
                        .orElse(null);

        return AchievementResponse.from(achievement, userAchievement);
    }

    private void ensureEnabled(User user) {
        if (!user.isEnabled()) {
            throw new NotFoundException("User not found");
        }
    }
}