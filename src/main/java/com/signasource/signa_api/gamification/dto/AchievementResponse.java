package com.signasource.signa_api.gamification.dto;

import com.signasource.signa_api.gamification.entity.Achievement;
import com.signasource.signa_api.gamification.entity.AchievementCriteriaType;
import com.signasource.signa_api.gamification.entity.UserAchievement;
import java.time.Instant;
import java.util.UUID;

public record AchievementResponse(
        UUID id,
        String code,
        String title,
        String description,
        String iconUrl,
        AchievementCriteriaType criteriaType,
        int criteriaValue,
        boolean active,
        boolean earned,
        Instant earnedAt) {

    public static AchievementResponse from(
            Achievement achievement, UserAchievement userAchievement) {
        return new AchievementResponse(
                achievement.getId(),
                achievement.getCode(),
                achievement.getTitle(),
                achievement.getDescription(),
                achievement.getIconUrl(),
                achievement.getCriteriaType(),
                achievement.getCriteriaValue(),
                achievement.isActive(),
                userAchievement != null,
                userAchievement != null ? userAchievement.getEarnedAt() : null);
    }
}