package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.PurchaseResponse;
import com.signasource.signa_api.gamification.entity.Purchase;
import com.signasource.signa_api.gamification.entity.PurchaseStatus;
import com.signasource.signa_api.gamification.entity.ShopItem;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.PurchaseRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.users.entity.User;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BoosterService {

    private final PurchaseRepository purchaseRepository;
    private final UserStatsRepository userStatsRepository;

    @Transactional
    public PurchaseResponse activate(User user, ShopItemType itemType) {
        ensureEnabled(user);
        ensureActivatable(itemType);

        Purchase purchase =
                purchaseRepository
                        .findFirstByUserAndShopItem_ItemTypeAndStatusOrderByPurchasedAtAsc(
                                user, itemType, PurchaseStatus.STORED)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "No available "
                                                        + itemType
                                                        + " booster in inventory"));

        UserStats stats =
                userStatsRepository
                        .findByUser(user)
                        .orElseThrow(() -> new NotFoundException("User stats not found"));

        applyEffect(stats, purchase.getShopItem());
        stats.setUpdatedAt(Instant.now());
        userStatsRepository.save(stats);

        purchase.setStatus(PurchaseStatus.ACTIVATED);
        purchase.setActivatedAt(Instant.now());
        purchaseRepository.save(purchase);

        return PurchaseResponse.from(purchase, stats);
    }

    private void applyEffect(UserStats stats, ShopItem item) {
        switch (item.getItemType()) {
            case UNLIMITED_LIVES -> applyUnlimitedLives(stats, item);
            case XP_MULTIPLIER -> applyXpMultiplier(stats, item);
            default ->
                    throw new InvalidInputException(
                            "Booster type " + item.getItemType() + " cannot be activated");
        }
    }

    private void applyUnlimitedLives(UserStats stats, ShopItem item) {
        stats.setUnlimitedLivesExpiresAt(
                Instant.now().plus(Duration.ofMinutes(item.getDurationMinutes())));
    }

    private void applyXpMultiplier(UserStats stats, ShopItem item) {
        stats.setXpMultiplier(item.getMultiplierValue());
        stats.setXpMultiplierExpiresAt(
                Instant.now().plus(Duration.ofMinutes(item.getDurationMinutes())));
    }

    private void ensureEnabled(User user) {
        if (!user.isEnabled()) {
            throw new NotFoundException("User not found");
        }
    }

    private void ensureActivatable(ShopItemType itemType) {
        if (itemType != ShopItemType.UNLIMITED_LIVES && itemType != ShopItemType.XP_MULTIPLIER) {
            throw new InvalidInputException("Booster type " + itemType + " cannot be activated");
        }
    }
}
