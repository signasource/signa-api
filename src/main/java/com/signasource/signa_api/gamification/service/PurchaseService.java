package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.AppliedEffectResponse;
import com.signasource.signa_api.gamification.dto.PurchaseResponse;
import com.signasource.signa_api.gamification.dto.ShopItemResponse;
import com.signasource.signa_api.gamification.dto.UserInventoryResponse;
import com.signasource.signa_api.gamification.entity.LivesMode;
import com.signasource.signa_api.gamification.entity.Purchase;
import com.signasource.signa_api.gamification.entity.PurchaseStatus;
import com.signasource.signa_api.gamification.entity.ShopItem;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.PurchaseRepository;
import com.signasource.signa_api.gamification.repository.ShopItemRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private static final int MYSTERY_CHEST_MIN_GEMS = 10;
    private static final int MYSTERY_CHEST_MAX_GEMS = 50;
    private static final int MYSTERY_CHEST_MULTIPLIER_DURATION_MINUTES = 30;
    private static final int MYSTERY_CHEST_UNLIMITED_LIVES_MINUTES = 15;

    private final ShopItemRepository shopItemRepository;
    private final PurchaseRepository purchaseRepository;
    private final UserStatsRepository userStatsRepository;

    @Transactional
    public PurchaseResponse purchaseForSelf(User buyer, UUID shopItemId) {
        ensureEnabled(buyer);

        ShopItem item = getActiveItem(shopItemId);
        UserStats stats = getOrCreateStats(buyer);
        debitGems(stats, item.getPriceGems());

        AppliedEffectResponse effect = null;
        PurchaseStatus status = PurchaseStatus.STORED;
        Instant activatedAt = null;
        if (!requiresActivation(item.getItemType())) {
            effect = applyItemEffect(stats, item);
            status = PurchaseStatus.ACTIVATED;
            activatedAt = Instant.now();
        }
        stats.setUpdatedAt(Instant.now());
        userStatsRepository.save(stats);

        Purchase purchase =
                purchaseRepository.save(
                        Purchase.builder()
                                .user(buyer)
                                .shopItem(item)
                                .gemsSpent(item.getPriceGems())
                                .purchasedAt(Instant.now())
                                .status(status)
                                .activatedAt(activatedAt)
                                .build());

        return new PurchaseResponse(
                purchase.getId(),
                ShopItemResponse.from(item),
                purchase.getGemsSpent(),
                purchase.getPurchasedAt(),
                purchase.getStatus(),
                purchase.getActivatedAt(),
                effect,
                UserInventoryResponse.from(stats));
    }

    @Transactional
    public PurchaseResponse claim(User user, ShopItemType itemType) {
        ensureEnabled(user);

        Purchase purchase =
                purchaseRepository
                        .findFirstByUserAndShopItem_ItemTypeAndStatusOrderByPurchasedAtAsc(
                                user, itemType, PurchaseStatus.PENDING)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "No pending " + itemType + " purchase to claim"));

        UserStats stats =
                userStatsRepository
                        .findByUser(user)
                        .orElseThrow(() -> new NotFoundException("User stats not found"));

        purchase.setStatus(PurchaseStatus.STORED);
        purchaseRepository.save(purchase);

        return PurchaseResponse.from(purchase, stats);
    }

    /**
     * Boosters (XP multiplier, unlimited lives) are stored in the inventory instead of applied
     * immediately on purchase; the user activates them later via {@code BoosterService}.
     */
    private boolean requiresActivation(ShopItemType itemType) {
        return itemType == ShopItemType.XP_MULTIPLIER || itemType == ShopItemType.UNLIMITED_LIVES;
    }

    AppliedEffectResponse applyItemEffect(UserStats stats, ShopItem item) {
        return item.getItemType() == ShopItemType.MYSTERY_CHEST
                ? openMysteryChest(stats)
                : applyConcreteEffect(stats, item);
    }

    private AppliedEffectResponse applyConcreteEffect(UserStats stats, ShopItem item) {
        return switch (item.getItemType()) {
            case GEMS -> {
                stats.setGems(stats.getGems() + item.getQuantity());
                yield new AppliedEffectResponse(
                        ShopItemType.GEMS, item.getQuantity(), null, null, null, null);
            }
            case LIFE -> {
                int granted = grantLives(stats, item.getQuantity());
                yield new AppliedEffectResponse(ShopItemType.LIFE, null, granted, null, null, null);
            }
            case STREAK_SHIELD -> {
                stats.setStreakShields(stats.getStreakShields() + item.getQuantity());
                yield new AppliedEffectResponse(
                        ShopItemType.STREAK_SHIELD, null, null, item.getQuantity(), null, null);
            }
            case XP_MULTIPLIER -> {
                grantXpMultiplier(stats, item.getMultiplierValue(), item.getDurationMinutes());
                yield new AppliedEffectResponse(
                        ShopItemType.XP_MULTIPLIER,
                        null,
                        null,
                        null,
                        item.getMultiplierValue(),
                        item.getDurationMinutes());
            }
            case UNLIMITED_LIVES -> {
                grantUnlimitedLives(stats, item.getDurationMinutes());
                yield new AppliedEffectResponse(
                        ShopItemType.UNLIMITED_LIVES,
                        null,
                        null,
                        null,
                        null,
                        item.getDurationMinutes());
            }
            case MYSTERY_CHEST ->
                    throw new IllegalStateException("Mystery chest must be resolved separately");
        };
    }

    private AppliedEffectResponse openMysteryChest(UserStats stats) {
        MysteryChestOutcome[] outcomes = MysteryChestOutcome.values();
        MysteryChestOutcome outcome =
                outcomes[ThreadLocalRandom.current().nextInt(outcomes.length)];

        return switch (outcome) {
            case GEMS -> {
                int amount =
                        ThreadLocalRandom.current()
                                .nextInt(MYSTERY_CHEST_MIN_GEMS, MYSTERY_CHEST_MAX_GEMS + 1);
                stats.setGems(stats.getGems() + amount);
                yield new AppliedEffectResponse(ShopItemType.GEMS, amount, null, null, null, null);
            }
            case LIFE -> {
                int granted = grantLives(stats, 1);
                yield new AppliedEffectResponse(ShopItemType.LIFE, null, granted, null, null, null);
            }
            case STREAK_SHIELD -> {
                stats.setStreakShields(stats.getStreakShields() + 1);
                yield new AppliedEffectResponse(
                        ShopItemType.STREAK_SHIELD, null, null, 1, null, null);
            }
            case XP_MULTIPLIER -> {
                double multiplier = ThreadLocalRandom.current().nextBoolean() ? 1.5 : 3.0;
                grantXpMultiplier(stats, multiplier, MYSTERY_CHEST_MULTIPLIER_DURATION_MINUTES);
                yield new AppliedEffectResponse(
                        ShopItemType.XP_MULTIPLIER,
                        null,
                        null,
                        null,
                        multiplier,
                        MYSTERY_CHEST_MULTIPLIER_DURATION_MINUTES);
            }
            case UNLIMITED_LIVES -> {
                grantUnlimitedLives(stats, MYSTERY_CHEST_UNLIMITED_LIVES_MINUTES);
                yield new AppliedEffectResponse(
                        ShopItemType.UNLIMITED_LIVES,
                        null,
                        null,
                        null,
                        null,
                        MYSTERY_CHEST_UNLIMITED_LIVES_MINUTES);
            }
        };
    }

    private int grantLives(UserStats stats, int quantity) {
        int current = stats.getCurrentLives() == null ? 0 : stats.getCurrentLives();
        int updated = Math.min(UserStats.MAX_LIVES, current + quantity);
        stats.setCurrentLives(updated);
        if (updated >= UserStats.MAX_LIVES) {
            stats.setNextLifeAt(null);
        }
        return updated - current;
    }

    private void grantXpMultiplier(UserStats stats, double multiplier, int durationMinutes) {
        stats.setXpMultiplier(multiplier);
        stats.setXpMultiplierExpiresAt(Instant.now().plus(durationMinutes, ChronoUnit.MINUTES));
    }

    private void grantUnlimitedLives(UserStats stats, int durationMinutes) {
        stats.setLivesMode(LivesMode.INFINITE);
        stats.setUnlimitedLivesExpiresAt(Instant.now().plus(durationMinutes, ChronoUnit.MINUTES));
    }

    void debitGems(UserStats stats, int price) {
        if (stats.getGems() < price) {
            throw new InvalidInputException("Insufficient gems");
        }
        stats.setGems(stats.getGems() - price);
    }

    ShopItem getActiveItem(UUID id) {
        ShopItem item =
                shopItemRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException("Shop item not found"));
        if (!item.isActive()) {
            throw new InvalidInputException("Shop item is not available");
        }
        return item;
    }

    UserStats getOrCreateStats(User user) {
        return userStatsRepository
                .findByUser(user)
                .orElseGet(() -> UserStats.builder().user(user).updatedAt(Instant.now()).build());
    }

    void ensureEnabled(User user) {
        if (!user.isEnabled()) {
            throw new NotFoundException("User not found");
        }
    }

    private enum MysteryChestOutcome {
        GEMS,
        LIFE,
        STREAK_SHIELD,
        XP_MULTIPLIER,
        UNLIMITED_LIVES
    }
}
