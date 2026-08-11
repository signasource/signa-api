package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.PurchaseResponse;
import com.signasource.signa_api.gamification.entity.LivesMode;
import com.signasource.signa_api.gamification.entity.Purchase;
import com.signasource.signa_api.gamification.entity.ShopItem;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.PurchaseRepository;
import com.signasource.signa_api.gamification.repository.ShopItemRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock private ShopItemRepository shopItemRepository;
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
        stats = UserStats.builder().id(UUID.randomUUID()).user(user).gems(1000).build();

        lenient().when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));
        lenient()
                .when(purchaseRepository.save(any(Purchase.class)))
                .thenAnswer(
                        invocation -> {
                            Purchase p = invocation.getArgument(0);
                            p.setId(UUID.randomUUID());
                            return p;
                        });
    }

    private ShopItem shopItem(ShopItemType type, int priceGems, int quantity) {
        return ShopItem.builder()
                .id(UUID.randomUUID())
                .code(type.name().toLowerCase())
                .title(type.name())
                .itemType(type)
                .priceGems(priceGems)
                .quantity(quantity)
                .active(true)
                .build();
    }

    @Test
    void purchaseForSelf_whenUserDisabled_throwsNotFound() {
        user.setEnabled(false);

        assertThrows(
                NotFoundException.class,
                () -> purchaseService.purchaseForSelf(user, UUID.randomUUID()));
    }

    @Test
    void purchaseForSelf_whenItemNotFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(shopItemRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> purchaseService.purchaseForSelf(user, id));
    }

    @Test
    void purchaseForSelf_whenItemInactive_throwsInvalidInput() {
        ShopItem item = shopItem(ShopItemType.GEMS, 10, 5);
        item.setActive(false);
        when(shopItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        assertThrows(
                InvalidInputException.class,
                () -> purchaseService.purchaseForSelf(user, item.getId()));
    }

    @Test
    void purchaseForSelf_whenInsufficientGems_throwsInvalidInput() {
        stats.setGems(5);
        ShopItem item = shopItem(ShopItemType.GEMS, 100, 10);
        when(shopItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        assertThrows(
                InvalidInputException.class,
                () -> purchaseService.purchaseForSelf(user, item.getId()));
    }

    @Test
    void purchaseForSelf_whenGemsItem_creditsGems() {
        ShopItem item = shopItem(ShopItemType.GEMS, 10, 25);
        when(shopItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        PurchaseResponse response = purchaseService.purchaseForSelf(user, item.getId());

        assertEquals(990 + 25, stats.getGems());
        assertEquals(25, response.effect().gemsGranted());
        assertEquals(ShopItemType.GEMS, response.effect().type());
    }

    @Test
    void purchaseForSelf_whenLifeItem_grantsLivesCappedAtMax() {
        stats.setCurrentLives(4);
        ShopItem item = shopItem(ShopItemType.LIFE, 20, 5);
        when(shopItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        PurchaseResponse response = purchaseService.purchaseForSelf(user, item.getId());

        assertEquals(UserStats.MAX_LIVES, stats.getCurrentLives());
        assertEquals(1, response.effect().livesGranted());
        assertEquals(ShopItemType.LIFE, response.effect().type());
    }

    @Test
    void purchaseForSelf_whenStreakShieldItem_addsShields() {
        stats.setStreakShields(1);
        ShopItem item = shopItem(ShopItemType.STREAK_SHIELD, 40, 7);
        when(shopItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        PurchaseResponse response = purchaseService.purchaseForSelf(user, item.getId());

        assertEquals(8, stats.getStreakShields());
        assertEquals(7, response.effect().streakShieldsGranted());
    }

    @Test
    void purchaseForSelf_whenXpMultiplierItem_setsMultiplierAndExpiry() {
        ShopItem item = shopItem(ShopItemType.XP_MULTIPLIER, 50, 1);
        item.setMultiplierValue(1.5);
        item.setDurationMinutes(30);
        when(shopItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        PurchaseResponse response = purchaseService.purchaseForSelf(user, item.getId());

        assertEquals(1.5, stats.getXpMultiplier());
        assertNotNull(stats.getXpMultiplierExpiresAt());
        assertEquals(1.5, response.effect().xpMultiplierValue());
        assertEquals(30, response.effect().durationMinutes());
    }

    @Test
    void purchaseForSelf_whenUnlimitedLivesItem_activatesInfiniteMode() {
        ShopItem item = shopItem(ShopItemType.UNLIMITED_LIVES, 80, 1);
        item.setDurationMinutes(15);
        when(shopItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        PurchaseResponse response = purchaseService.purchaseForSelf(user, item.getId());

        assertEquals(LivesMode.INFINITE, stats.getLivesMode());
        assertTrue(stats.hasActiveUnlimitedLives());
        assertEquals(15, response.effect().durationMinutes());
    }

    @Test
    void purchaseForSelf_whenNoStatsExist_createsNewStats() {
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.empty());
        ShopItem item = shopItem(ShopItemType.STREAK_SHIELD, 0, 1);
        when(shopItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        PurchaseResponse response = purchaseService.purchaseForSelf(user, item.getId());

        assertEquals(1, response.inventory().streakShields());
    }

    @Test
    void purchaseForSelf_whenMysteryChestItem_resolvesToAConcreteReward() {
        ShopItem item = shopItem(ShopItemType.MYSTERY_CHEST, 0, 1);
        when(shopItemRepository.findById(item.getId())).thenReturn(Optional.of(item));

        Set<ShopItemType> seenTypes = new HashSet<>();
        for (int i = 0; i < 300; i++) {
            PurchaseResponse response = purchaseService.purchaseForSelf(user, item.getId());
            seenTypes.add(response.effect().type());
            assertTrue(response.effect().type() != ShopItemType.MYSTERY_CHEST);
        }

        assertTrue(seenTypes.size() > 1);
    }
}
