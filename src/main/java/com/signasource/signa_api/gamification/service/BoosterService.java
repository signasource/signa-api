package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.gamification.dto.BoosterActivationResponse;
import com.signasource.signa_api.gamification.entity.Purchase;
import com.signasource.signa_api.gamification.entity.PurchaseStatus;
import com.signasource.signa_api.gamification.entity.ShopItem;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.PurchaseRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.users.entity.User;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BoosterService {

    private final PurchaseRepository purchaseRepository;
    private final UserStatsRepository userStatsRepository;

    @Transactional
    public BoosterActivationResponse activate(User user, UUID purchaseId) {
        ensureEnabled(user);

        Purchase purchase =
                purchaseRepository
                        .findByIdAndUser(purchaseId, user)
                        .orElseThrow(() -> new NotFoundException("Purchase not found"));

        if (purchase.getStatus() == PurchaseStatus.ACTIVATED) {
            throw new ResourceAlreadyInUseException("Booster has already been activated");
        }

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

        return BoosterActivationResponse.from(purchase, stats);
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
}
