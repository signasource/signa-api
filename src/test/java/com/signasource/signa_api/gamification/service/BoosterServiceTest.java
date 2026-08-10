package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.gamification.dto.BoosterActivationResponse;
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
class BoosterServiceTest {

    @Mock private PurchaseRepository purchaseRepository;

    @Mock private UserStatsRepository userStatsRepository;

    @InjectMocks private BoosterService boosterService;

    private User user;
    private UserStats stats;
    private UUID purchaseId;

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

        purchaseId = UUID.randomUUID();
    }

    private Purchase pendingPurchase(ShopItem shopItem) {
        return Purchase.builder()
                .id(purchaseId)
                .user(user)
                .shopItem(shopItem)
                .gemsSpent(shopItem.getPriceGems())
                .purchasedAt(Instant.now())
                .status(PurchaseStatus.PENDING)
                .build();
    }

    @Test
    void activate_whenStreakShield_incrementsShieldCount() {
        ShopItem item =
                ShopItem.builder()
                        .id(UUID.randomUUID())
                        .code("streak_shield")
                        .itemType(ShopItemType.STREAK_SHIELD)
                        .priceGems(20)
                        .quantity(1)
                        .build();
        Purchase purchase = pendingPurchase(item);
        when(purchaseRepository.findByIdAndUser(purchaseId, user))
                .thenReturn(Optional.of(purchase));
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));

        BoosterActivationResponse response = boosterService.activate(user, purchaseId);

        assertEquals(1, stats.getStreakShields());
        assertEquals(PurchaseStatus.ACTIVATED, purchase.getStatus());
        assertNotNull(purchase.getActivatedAt());
        assertEquals(PurchaseStatus.ACTIVATED, response.status());
        verify(userStatsRepository).save(stats);
        verify(purchaseRepository).save(purchase);
    }

    @Test
    void activate_whenGems_addsGemsToBalance() {
        ShopItem item =
                ShopItem.builder()
                        .id(UUID.randomUUID())
                        .code("gems_pack")
                        .itemType(ShopItemType.GEMS)
                        .priceGems(0)
                        .quantity(100)
                        .build();
        Purchase purchase = pendingPurchase(item);
        when(purchaseRepository.findByIdAndUser(purchaseId, user))
                .thenReturn(Optional.of(purchase));
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));

        boosterService.activate(user, purchaseId);

        assertEquals(150, stats.getGems());
    }

    @Test
    void activate_whenLifeAndLimitedMode_addsLifeCappedAtMax() {
        stats.setCurrentLives(4);
        ShopItem item =
                ShopItem.builder()
                        .id(UUID.randomUUID())
                        .code("extra_life")
                        .itemType(ShopItemType.LIFE)
                        .priceGems(10)
                        .quantity(5)
                        .build();
        Purchase purchase = pendingPurchase(item);
        when(purchaseRepository.findByIdAndUser(purchaseId, user))
                .thenReturn(Optional.of(purchase));
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));

        boosterService.activate(user, purchaseId);

        assertEquals(5, stats.getCurrentLives());
    }

    @Test
    void activate_whenLifeAndInfiniteMode_throwsInvalidInput() {
        stats.setLivesMode(LivesMode.INFINITE);
        ShopItem item =
                ShopItem.builder()
                        .id(UUID.randomUUID())
                        .code("extra_life")
                        .itemType(ShopItemType.LIFE)
                        .priceGems(10)
                        .quantity(1)
                        .build();
        Purchase purchase = pendingPurchase(item);
        when(purchaseRepository.findByIdAndUser(purchaseId, user))
                .thenReturn(Optional.of(purchase));
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));

        assertThrows(InvalidInputException.class, () -> boosterService.activate(user, purchaseId));
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void activate_whenXpMultiplier_setsMultiplierAndExpiry() {
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
        when(purchaseRepository.findByIdAndUser(purchaseId, user))
                .thenReturn(Optional.of(purchase));
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));

        boosterService.activate(user, purchaseId);

        assertEquals(2.0, stats.getXpMultiplier());
        assertTrue(stats.getXpMultiplierExpiresAt().isAfter(Instant.now()));
        assertTrue(stats.hasActiveXpMultiplier());
    }

    @Test
    void activate_whenPurchaseAlreadyActivated_throwsResourceAlreadyInUse() {
        ShopItem item =
                ShopItem.builder()
                        .id(UUID.randomUUID())
                        .code("streak_shield")
                        .itemType(ShopItemType.STREAK_SHIELD)
                        .priceGems(20)
                        .quantity(1)
                        .build();
        Purchase purchase = pendingPurchase(item);
        purchase.setStatus(PurchaseStatus.ACTIVATED);
        purchase.setActivatedAt(Instant.now());
        when(purchaseRepository.findByIdAndUser(purchaseId, user))
                .thenReturn(Optional.of(purchase));

        assertThrows(
                ResourceAlreadyInUseException.class,
                () -> boosterService.activate(user, purchaseId));
        verify(userStatsRepository, never()).findByUser(any());
    }

    @Test
    void activate_whenPurchaseNotFound_throwsNotFound() {
        when(purchaseRepository.findByIdAndUser(purchaseId, user)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> boosterService.activate(user, purchaseId));
    }

    @Test
    void activate_whenUserStatsNotFound_throwsNotFound() {
        ShopItem item =
                ShopItem.builder()
                        .id(UUID.randomUUID())
                        .code("streak_shield")
                        .itemType(ShopItemType.STREAK_SHIELD)
                        .priceGems(20)
                        .quantity(1)
                        .build();
        Purchase purchase = pendingPurchase(item);
        when(purchaseRepository.findByIdAndUser(purchaseId, user))
                .thenReturn(Optional.of(purchase));
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> boosterService.activate(user, purchaseId));
    }

    @Test
    void activate_whenUserDisabled_throwsNotFound() {
        user.setEnabled(false);

        assertThrows(NotFoundException.class, () -> boosterService.activate(user, purchaseId));
    }
}
