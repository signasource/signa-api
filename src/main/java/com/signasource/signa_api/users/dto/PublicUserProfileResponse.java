package com.signasource.signa_api.users.dto;

import com.signasource.signa_api.gamification.dto.AchievementResponse;
import com.signasource.signa_api.gamification.dto.DailyXpResponse;
import com.signasource.signa_api.learning.dto.CourseProgressResponse;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.UUID;

/** With {@code visible} false the account is private to the viewer: identity only, no progress. */
public record PublicUserProfileResponse(
        UUID id,
        String username,
        String name,
        String profileHeaderColor,
        RelationStatus relation,
        boolean visible,
        PublicUserStatsResponse stats,
        List<DailyXpResponse> weeklyXp,
        List<AchievementResponse> achievements,
        List<CourseProgressResponse> courses) {

    public static PublicUserProfileResponse hidden(User user, RelationStatus relation) {
        return new PublicUserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getName(),
                null,
                relation,
                false,
                PublicUserStatsResponse.EMPTY,
                List.of(),
                List.of(),
                List.of());
    }
}
