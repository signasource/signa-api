package com.signasource.signa_api.users.dto;

import com.signasource.signa_api.gamification.dto.AchievementResponse;
import com.signasource.signa_api.gamification.dto.DailyXpResponse;
import com.signasource.signa_api.learning.dto.CourseProgressResponse;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.UUID;

/**
 * Another user's profile, as shown by the app's read-only profile screen.
 *
 * <p>When {@code visible} is false the account is private and the viewer is not a friend: identity
 * and {@code relation} are still returned — so they can send a friend request — but every progress
 * field comes back empty.
 */
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

    /** Private account seen by a stranger: who they are, and nothing else. */
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
