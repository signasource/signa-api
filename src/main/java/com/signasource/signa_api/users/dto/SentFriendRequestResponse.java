package com.signasource.signa_api.users.dto;

import com.signasource.signa_api.users.entity.Friendship;
import java.time.LocalDateTime;
import java.util.UUID;

public record SentFriendRequestResponse(
        UUID addresseeId,
        String addresseeUsername,
        String addresseeName,
        LocalDateTime requestedAt) {

    public static SentFriendRequestResponse from(Friendship friendship) {
        return new SentFriendRequestResponse(
                friendship.getAddressee().getId(),
                friendship.getAddressee().getUsername(),
                friendship.getAddressee().getName(),
                friendship.getCreatedAt());
    }
}
