package com.signasource.signa_api.gamification.dto;

import com.signasource.signa_api.gamification.entity.Gift;
import com.signasource.signa_api.gamification.entity.GiftStatus;
import java.time.Instant;
import java.util.UUID;

public record GiftResponse(
        UUID id,
        ShopItemResponse item,
        UUID senderId,
        String senderUsername,
        UUID recipientId,
        String recipientUsername,
        String message,
        GiftStatus status,
        Instant sentAt,
        Instant claimedAt,
        Instant expiresAt) {

    public static GiftResponse from(Gift gift) {
        GiftStatus effectiveStatus =
                gift.getStatus() == GiftStatus.PENDING
                                && gift.getExpiresAt() != null
                                && gift.getExpiresAt().isBefore(Instant.now())
                        ? GiftStatus.EXPIRED
                        : gift.getStatus();

        return new GiftResponse(
                gift.getId(),
                ShopItemResponse.from(gift.getShopItem()),
                gift.getSender().getId(),
                gift.getSender().getUsername(),
                gift.getRecipient().getId(),
                gift.getRecipient().getUsername(),
                gift.getMessage(),
                effectiveStatus,
                gift.getSentAt(),
                gift.getClaimedAt(),
                gift.getExpiresAt());
    }
}
