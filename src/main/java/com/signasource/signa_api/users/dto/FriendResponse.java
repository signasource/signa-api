package com.signasource.signa_api.users.dto;

import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.User;
import java.time.LocalDateTime;
import java.util.UUID;

/** Stats fall back to zero when the friend has no {@link UserStats} row yet. */
public record FriendResponse(
        UUID id,
        String username,
        String name,
        LocalDateTime acceptedAt,
        int currentStreak,
        long totalXp,
        int learnedSignsCount) {

    public static FriendResponse from(Friendship friendship, User currentUser, UserStats stats) {
        var friend =
                friendship.getRequester().getId().equals(currentUser.getId())
                        ? friendship.getAddressee()
                        : friendship.getRequester();

        return new FriendResponse(
                friend.getId(),
                friend.getUsername(),
                friend.getName(),
                friendship.getUpdatedAt(),
                stats != null ? stats.getCurrentStreak() : 0,
                stats != null ? stats.getTotalXp() : 0L,
                stats != null ? stats.getLearnedSignsCount() : 0);
    }
}
