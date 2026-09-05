package com.signasource.signa_api.users.dto;

import com.signasource.signa_api.users.entity.FriendEventType;
import java.time.Instant;
import java.util.UUID;

/** Events are derived, not stored: ({@code eventType}, {@code eventRefId}) is their identity. */
public record FriendEventResponse(
        UUID friendId,
        String friendUsername,
        String friendName,
        FriendEventType eventType,
        UUID eventRefId,
        String subject,
        String context,
        boolean liked,
        Instant createdAt) {}
