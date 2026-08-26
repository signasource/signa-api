package com.signasource.signa_api.users.dto;

import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.User;
import java.time.LocalDateTime;
import java.util.UUID;

public record FriendResponse(UUID id, String username, String name, LocalDateTime acceptedAt) {
    public static FriendResponse from(Friendship friendship, User currentUser) {
        var friend =
                friendship.getRequester().getId().equals(currentUser.getId())
                        ? friendship.getAddressee()
                        : friendship.getRequester();

        return new FriendResponse(
                friend.getId(), friend.getUsername(), friend.getName(), friendship.getUpdatedAt());
    }
}
