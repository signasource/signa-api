package com.signasource.signa_api.users.dto;

import java.util.UUID;

/**
 * A user matched by {@code GET /users/search}, resolved against the caller so the client can render
 * the right action (add / accept / cancel / unblock) without a second round-trip.
 */
public record UserSearchResultResponse(
        UUID id, String username, String name, RelationStatus relation, long mutualFriends) {}
