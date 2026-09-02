package com.signasource.signa_api.users.dto;

import com.signasource.signa_api.users.entity.Friendship;
import java.time.LocalDateTime;
import java.util.UUID;

public record FriendRequestResponse(
        UUID requesterId,
        String requesterUsername,
        String requesterName,
        LocalDateTime requestedAt) {
    public static FriendRequestResponse from(Friendship friendship) {
        return new FriendRequestResponse(
                friendship.getRequester().getId(),
                friendship.getRequester().getUsername(),
                friendship.getRequester().getName(),
                friendship.getCreatedAt());
    }
}
