package com.signasource.signa_api.users.dto;

import java.util.UUID;

/** Resolved against the caller, so the client can render the right action in one round-trip. */
public record UserSearchResultResponse(
        UUID id, String username, String name, RelationStatus relation, long mutualFriends) {}
