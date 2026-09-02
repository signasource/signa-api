package com.signasource.signa_api.users.dto;

import java.time.Instant;
import java.util.UUID;

public record FriendEventResponse(
        UUID friendId,
        String friendUsername,
        String friendName,
        String eventType,
        String eventDescription,
        Instant createdAt) {}
