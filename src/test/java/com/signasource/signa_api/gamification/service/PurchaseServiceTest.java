package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.PurchaseResponse;
import com.signasource.signa_api.gamification.entity.LivesMode;
import com.signasource.signa_api.gamification.entity.Purchase;
import com.signasource.signa_api.gamification.entity.PurchaseStatus;
import com.signasource.signa_api.gamification.entity.ShopItem;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.PurchaseRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock private PurchaseRepository purchaseRepository;

    @Mock private UserStatsRepository userStatsRepository;

    @InjectMocks private PurchaseService purchaseService;

    private User user;
    private UserStats stats;

    @BeforeEach
    void setUp() {
        user =
                User.builder()
                        .id(UUID.randomUUID())
                        .email("user@example.com")
                        .username("testuser")
                        .name("Test User")
                        .passwordHash("hashed")
                        .role(Role.USER)
                        .enabled(true)
                        .build();

        stats =
                UserStats.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .gems(50)
                        .streakShields(0)
                        .livesMode(LivesMode.LIMITED)
                        .currentLives(3)
                        .xpMultiplier(1.0)
                        .updatedAt(Instant.now())
                        .build();
    }

    private Purchase pendingPurchase(ShopItem shopItem) {
        return Purchase.builder()
                .id(UUID.randomUUID())
                .user(user)
                .shopItem(shopItem)
                .gemsSpent(shopItem.getPriceGems())
                .purchasedAt(Instant.now())
                .status(PurchaseStatus.PENDING)
                .build();
    }

    @Test
    void claim_whenPendingPurchaseExists_movesItToStored() {
        ShopItem item =
                ShopItem.builder()
                        .id(UUID.randomUUID())
                        .code("xp_boost")
                        .itemType(ShopItemType.XP_MULTIPLIER)
                        .priceGems(30)
                        .quantity(1)
                        .durationMinutes(30)
                        .multiplierValue(2.0)
                        .build();
        Purchase purchase = pendingPurchase(item);
        when(purchaseRepository.findFirstByUserAndShopItem_ItemTypeAndStatusOrderByPurchasedAtAsc(
                        user, ShopItemType.XP_MULTIPLIER, PurchaseStatus.PENDING))
                .thenReturn(Optional.of(purchase));
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));

        PurchaseResponse response = purchaseService.claim(user, ShopItemType.XP_MULTIPLIER);

        assertEquals(PurchaseStatus.STORED, purchase.getStatus());
        assertEquals(PurchaseStatus.STORED, response.status());
        assertNull(response.activatedAt());
        verify(purchaseRepository).save(purchase);
    }

    @Test
    void claim_allowsItemTypesThatCannotBeActivatedYet() {
        ShopItem item =
                ShopItem.builder()
                        .id(UUID.randomUUID())
                        .code("streak_shield")
                        .itemType(ShopItemType.STREAK_SHIELD)
                        .priceGems(15)
                        .quantity(1)
                        .build();
        Purchase purchase = pendingPurchase(item);
        when(purchaseRepository.findFirstByUserAndShopItem_ItemTypeAndStatusOrderByPurchasedAtAsc(
                        user, ShopItemType.STREAK_SHIELD, PurchaseStatus.PENDING))
                .thenReturn(Optional.of(purchase));
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));

        PurchaseResponse response = purchaseService.claim(user, ShopItemType.STREAK_SHIELD);

        assertEquals(PurchaseStatus.STORED, response.status());
    }

    @Test
    void claim_whenNoPendingPurchaseOfType_throwsNotFound() {
        when(purchaseRepository.findFirstByUserAndShopItem_ItemTypeAndStatusOrderByPurchasedAtAsc(
                        user, ShopItemType.XP_MULTIPLIER, PurchaseStatus.PENDING))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> purchaseService.claim(user, ShopItemType.XP_MULTIPLIER));
    }

    @Test
    void claim_whenUserStatsNotFound_throwsNotFound() {
        ShopItem item =
                ShopItem.builder()
                        .id(UUID.randomUUID())
                        .code("xp_boost")
                        .itemType(ShopItemType.XP_MULTIPLIER)
                        .priceGems(30)
                        .quantity(1)
                        .durationMinutes(30)
                        .multiplierValue(2.0)
                        .build();
        Purchase purchase = pendingPurchase(item);
        when(purchaseRepository.findFirstByUserAndShopItem_ItemTypeAndStatusOrderByPurchasedAtAsc(
                        user, ShopItemType.XP_MULTIPLIER, PurchaseStatus.PENDING))
                .thenReturn(Optional.of(purchase));
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> purchaseService.claim(user, ShopItemType.XP_MULTIPLIER));
    }

    @Test
    void claim_whenUserDisabled_throwsNotFound() {
        user.setEnabled(false);

        assertThrows(
                NotFoundException.class,
                () -> purchaseService.claim(user, ShopItemType.XP_MULTIPLIER));
    }
}
