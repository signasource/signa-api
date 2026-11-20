package com.signasource.signa_api.users.dto;

import java.util.UUID;

/**
 * Resolved against the caller, so the client can render the right action in one round-trip. {@code
 * profileVisible} is false for a private account the caller isn't friends with: it can receive a
 * request, but its profile stays closed until they are friends.
 */
public record UserSearchResultResponse(
        UUID id,
        String username,
        String name,
        RelationStatus relation,
        long mutualFriends,
        String avatarUrl,
        boolean profileVisible) {}
