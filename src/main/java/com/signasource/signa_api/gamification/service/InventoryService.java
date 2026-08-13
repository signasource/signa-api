package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.PurchaseRequest;
import com.signasource.signa_api.gamification.dto.PurchaseResponse;
import com.signasource.signa_api.gamification.dto.ShopItemResponse;
import com.signasource.signa_api.gamification.dto.UserInventoryResponse;
import com.signasource.signa_api.gamification.entity.Purchase;
import com.signasource.signa_api.gamification.entity.ShopItem;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.PurchaseRepository;
import com.signasource.signa_api.gamification.repository.ShopItemRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final UserStatsRepository userStatsRepository;
    private final ShopItemRepository shopItemRepository;
    private final PurchaseRepository purchaseRepository;

    @Transactional(readOnly = true)
    public UserInventoryResponse getInventory(User user) {
        ensureEnabled(user);

        UserStats stats =
                userStatsRepository
                        .findByUser(user)
                        .orElseThrow(() -> new NotFoundException("User stats not found"));

        return UserInventoryResponse.from(stats);
    }

    @Transactional(readOnly = true)
    public List<ShopItemResponse> getShop(ShopItemType itemType, Integer maxPrice) {
        return shopItemRepository.findActiveWithFilters(itemType, maxPrice).stream()
                .map(ShopItemResponse::from)
                .toList();
    }

    @Transactional
    public PurchaseResponse purchase(User user, PurchaseRequest request) {
        ensureEnabled(user);

        ShopItem item =
                shopItemRepository
                        .findById(request.shopItemId())
                        .orElseThrow(() -> new NotFoundException("Shop item not found"));

        if (!item.isActive()) {
            throw new InvalidInputException("Shop item is not available");
        }

        UserStats stats =
                userStatsRepository
                        .findByUser(user)
                        .orElseThrow(() -> new NotFoundException("User stats not found"));

        if (stats.getGems() < item.getPriceGems()) {
            throw new InvalidInputException(
                    "Insufficient gems. Required: "
                            + item.getPriceGems()
                            + ", Available: "
                            + stats.getGems());
        }

        stats.setGems(stats.getGems() - item.getPriceGems());
        stats.setUpdatedAt(Instant.now());

        applyItemEffect(stats, item);

        userStatsRepository.save(stats);

        Purchase purchase =
                Purchase.builder()
                        .user(user)
                        .shopItem(item)
                        .gemsSpent(item.getPriceGems())
                        .purchasedAt(Instant.now())
                        .build();

        purchaseRepository.save(purchase);

        return PurchaseResponse.from(purchase);
    }

    private void applyItemEffect(UserStats stats, ShopItem item) {
        switch (item.getItemType()) {
            case STREAK_SHIELD ->
                    stats.setStreakShields(stats.getStreakShields() + item.getQuantity());
            case LIFE ->
                    stats.setCurrentLives(
                            Math.min(
                                    5,
                                    (stats.getCurrentLives() != null ? stats.getCurrentLives() : 0)
                                            + item.getQuantity()));
            case XP_MULTIPLIER -> {
                if (item.getMultiplierValue() != null && item.getDurationMinutes() != null) {
                    stats.setXpMultiplier(item.getMultiplierValue());
                    stats.setXpMultiplierExpiresAt(
                            Instant.now().plus(item.getDurationMinutes(), ChronoUnit.MINUTES));
                }
            }
            case GEMS -> stats.setGems(stats.getGems() + item.getQuantity());
        }
    }

    private void ensureEnabled(User user) {
        if (!user.isEnabled()) {
            throw new NotFoundException("User not found");
        }
    }
}
