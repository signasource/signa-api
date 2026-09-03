package com.signasource.signa_api.users.dto;

import com.signasource.signa_api.users.entity.FriendEventType;
import java.time.Instant;
import java.util.UUID;

/**
 * One entry of the friends activity feed.
 *
 * <p>Events are derived on the fly from the friend's achievements and learned signs, so {@code
 * eventType} + {@code eventRefId} — the id of the row the event came from — is what identifies it,
 * and what the like endpoints address.
 *
 * <p>{@code subject} and {@code context} are the raw pieces of the event (achievement title and its
 * description, or the learned sign and its course); the sentence itself is composed by the client
 * so the copy stays in the UI language.
 */
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
