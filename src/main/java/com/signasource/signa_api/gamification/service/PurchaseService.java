package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.PurchaseResponse;
import com.signasource.signa_api.gamification.entity.Purchase;
import com.signasource.signa_api.gamification.entity.PurchaseStatus;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.PurchaseRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.users.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final UserStatsRepository userStatsRepository;

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

    private void ensureEnabled(User user) {
        if (!user.isEnabled()) {
            throw new NotFoundException("User not found");
        }
    }
}
