package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.gamification.dto.AppliedEffectResponse;
import com.signasource.signa_api.gamification.dto.GiftClaimResponse;
import com.signasource.signa_api.gamification.dto.GiftResponse;
import com.signasource.signa_api.gamification.dto.UserInventoryResponse;
import com.signasource.signa_api.gamification.entity.Gift;
import com.signasource.signa_api.gamification.entity.GiftStatus;
import com.signasource.signa_api.gamification.entity.Purchase;
import com.signasource.signa_api.gamification.entity.ShopItem;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.GiftRepository;
import com.signasource.signa_api.gamification.repository.PurchaseRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.users.entity.FriendshipStatus;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.FriendshipRepository;
import com.signasource.signa_api.users.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GiftService {

    private static final int GIFT_EXPIRY_DAYS = 7;

    private final GiftRepository giftRepository;
    private final PurchaseRepository purchaseRepository;
    private final UserStatsRepository userStatsRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final PurchaseService purchaseService;

    @Transactional
    public GiftResponse sendGift(User sender, UUID shopItemId, UUID recipientId, String message) {
        purchaseService.ensureEnabled(sender);

        if (sender.getId().equals(recipientId)) {
            throw new InvalidInputException("You cannot send a gift to yourself");
        }

        User recipient =
                userRepository
                        .findById(recipientId)
                        .filter(User::isEnabled)
                        .orElseThrow(() -> new NotFoundException("User not found"));

        ensureFriends(sender, recipient);

        ShopItem item = purchaseService.getActiveItem(shopItemId);
        UserStats senderStats = purchaseService.getOrCreateStats(sender);
        purchaseService.debitGems(senderStats, item.getPriceGems());
        senderStats.setUpdatedAt(Instant.now());
        userStatsRepository.save(senderStats);

        Instant now = Instant.now();
        purchaseRepository.save(
                Purchase.builder()
                        .user(sender)
                        .shopItem(item)
                        .gemsSpent(item.getPriceGems())
                        .purchasedAt(now)
                        .build());

        Gift gift =
                giftRepository.save(
                        Gift.builder()
                                .sender(sender)
                                .recipient(recipient)
                                .shopItem(item)
                                .message(message)
                                .status(GiftStatus.PENDING)
                                .sentAt(now)
                                .expiresAt(now.plus(GIFT_EXPIRY_DAYS, ChronoUnit.DAYS))
                                .build());

        return GiftResponse.from(gift);
    }

    @Transactional
    public GiftClaimResponse claimGift(User user, UUID giftId) {
        purchaseService.ensureEnabled(user);

        Gift gift =
                giftRepository
                        .findByIdAndRecipient(giftId, user)
                        .orElseThrow(() -> new NotFoundException("Gift not found"));

        if (gift.getStatus() == GiftStatus.CLAIMED) {
            throw new ResourceAlreadyInUseException("Gift has already been claimed");
        }

        if (isExpired(gift)) {
            gift.setStatus(GiftStatus.EXPIRED);
            giftRepository.save(gift);
            throw new InvalidInputException("Gift has expired");
        }

        UserStats stats = purchaseService.getOrCreateStats(user);
        AppliedEffectResponse effect = purchaseService.applyItemEffect(stats, gift.getShopItem());
        stats.setUpdatedAt(Instant.now());
        userStatsRepository.save(stats);

        gift.setStatus(GiftStatus.CLAIMED);
        gift.setClaimedAt(Instant.now());
        giftRepository.save(gift);

        return new GiftClaimResponse(
                GiftResponse.from(gift), effect, UserInventoryResponse.from(stats));
    }

    @Transactional(readOnly = true)
    public List<GiftResponse> getReceivedGifts(User user, GiftStatus status) {
        purchaseService.ensureEnabled(user);

        List<Gift> gifts =
                status == null
                        ? giftRepository.findByRecipientOrderBySentAtDesc(user)
                        : giftRepository.findByRecipientAndStatusOrderBySentAtDesc(user, status);

        return gifts.stream().map(GiftResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<GiftResponse> getSentGifts(User user) {
        purchaseService.ensureEnabled(user);

        return giftRepository.findBySenderOrderBySentAtDesc(user).stream()
                .map(GiftResponse::from)
                .toList();
    }

    private void ensureFriends(User sender, User recipient) {
        boolean friends =
                friendshipRepository
                        .findFriendshipBetween(sender, recipient)
                        .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                        .isPresent();

        if (!friends) {
            throw new InvalidInputException("You can only send gifts to friends");
        }
    }

    private boolean isExpired(Gift gift) {
        return gift.getExpiresAt() != null && gift.getExpiresAt().isBefore(Instant.now());
    }
}
