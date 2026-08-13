package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.PurchaseRequest;
import com.signasource.signa_api.gamification.dto.PurchaseResponse;
import com.signasource.signa_api.gamification.dto.ShopItemResponse;
import com.signasource.signa_api.gamification.dto.UserInventoryResponse;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock private UserStatsRepository userStatsRepository;

    @Mock private ShopItemRepository shopItemRepository;

    @Mock private PurchaseRepository purchaseRepository;

    @InjectMocks private InventoryService inventoryService;

    private User user;

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
    }

    @Test
    void getInventory_whenUserEnabledAndStatsExist_returnsInventoryWithActiveMultiplier() {
        UserStats stats =
                UserStats.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .gems(150)
                        .streakShields(2)
                        .livesMode(LivesMode.LIMITED)
                        .currentLives(3)
                        .nextLifeAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                        .xpMultiplier(2.0)
                        .xpMultiplierExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                        .updatedAt(Instant.now())
                        .build();
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));

        UserInventoryResponse response = inventoryService.getInventory(user);

        assertEquals(150, response.gems());
        assertEquals(2, response.streakShields());
        assertEquals(LivesMode.LIMITED, response.livesMode());
        assertEquals(3, response.currentLives());
        assertEquals(2.0, response.effectiveXpMultiplier());
        assertTrue(response.xpMultiplierActive());
    }

    @Test
    void getInventory_whenXpMultiplierExpired_returnsInactiveMultiplier() {
        UserStats stats =
                UserStats.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .gems(0)
                        .streakShields(0)
                        .livesMode(LivesMode.LIMITED)
                        .currentLives(5)
                        .xpMultiplier(2.0)
                        .xpMultiplierExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS))
                        .updatedAt(Instant.now())
                        .build();
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));

        UserInventoryResponse response = inventoryService.getInventory(user);

        assertEquals(1.0, response.effectiveXpMultiplier());
        assertFalse(response.xpMultiplierActive());
    }

    @Test
    void getInventory_whenLivesModeInfinite_returnsNullCurrentLives() {
        UserStats stats =
                UserStats.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .gems(0)
                        .streakShields(0)
                        .livesMode(LivesMode.INFINITE)
                        .currentLives(null)
                        .xpMultiplier(1.0)
                        .updatedAt(Instant.now())
                        .build();
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));

        UserInventoryResponse response = inventoryService.getInventory(user);

        assertEquals(LivesMode.INFINITE, response.livesMode());
        assertNull(response.currentLives());
    }

    @Test
    void getInventory_whenUserDisabled_throwsNotFound() {
        user.setEnabled(false);

        assertThrows(NotFoundException.class, () -> inventoryService.getInventory(user));
    }

    @Test
    void getInventory_whenStatsNotFound_throwsNotFound() {
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> inventoryService.getInventory(user));
    }

    @Test
    void getShop_withoutFilters_returnsAllActiveItems() {
        ShopItem item1 =
                ShopItem.builder()
                        .id(UUID.randomUUID())
                        .code("SHIELD_1")
                        .title("Streak Shield")
                        .description("Protects your streak")
                        .itemType(ShopItemType.STREAK_SHIELD)
                        .priceGems(50)
                        .quantity(1)
                        .active(true)
                        .build();
        ShopItem item2 =
                ShopItem.builder()
                        .id(UUID.randomUUID())
                        .code("LIFE_1")
                        .title("Extra Life")
                        .description("Get one more life")
                        .itemType(ShopItemType.LIFE)
                        .priceGems(75)
                        .quantity(1)
                        .active(true)
                        .build();
        when(shopItemRepository.findActiveWithFilters(null, null))
                .thenReturn(List.of(item1, item2));

        List<ShopItemResponse> response = inventoryService.getShop(null, null);

        assertEquals(2, response.size());
        assertEquals("SHIELD_1", response.get(0).code());
        assertEquals("LIFE_1", response.get(1).code());
        verify(shopItemRepository).findActiveWithFilters(null, null);
    }

    @Test
    void getShop_withItemTypeFilter_returnsFilteredItems() {
        ShopItem item =
                ShopItem.builder()
                        .id(UUID.randomUUID())
                        .code("SHIELD_1")
                        .title("Streak Shield")
                        .description("Protects your streak")
                        .itemType(ShopItemType.STREAK_SHIELD)
                        .priceGems(50)
                        .quantity(1)
                        .active(true)
                        .build();
        when(shopItemRepository.findActiveWithFilters(ShopItemType.STREAK_SHIELD, null))
                .thenReturn(List.of(item));

        List<ShopItemResponse> response =
                inventoryService.getShop(ShopItemType.STREAK_SHIELD, null);

        assertEquals(1, response.size());
        assertEquals(ShopItemType.STREAK_SHIELD, response.get(0).itemType());
        verify(shopItemRepository).findActiveWithFilters(ShopItemType.STREAK_SHIELD, null);
    }

    @Test
    void getShop_withPriceFilter_returnsItemsUnderPrice() {
        ShopItem item =
                ShopItem.builder()
                        .id(UUID.randomUUID())
                        .code("SHIELD_1")
                        .title("Streak Shield")
                        .description("Protects your streak")
                        .itemType(ShopItemType.STREAK_SHIELD)
                        .priceGems(50)
                        .quantity(1)
                        .active(true)
                        .build();
        when(shopItemRepository.findActiveWithFilters(null, 50)).thenReturn(List.of(item));

        List<ShopItemResponse> response = inventoryService.getShop(null, 50);

        assertEquals(1, response.size());
        assertTrue(response.get(0).priceGems() <= 50);
        verify(shopItemRepository).findActiveWithFilters(null, 50);
    }

    @Test
    void purchase_withSufficientGems_successfullyPurchasesItem() {
        UUID itemId = UUID.randomUUID();
        ShopItem item =
                ShopItem.builder()
                        .id(itemId)
                        .code("SHIELD_1")
                        .title("Streak Shield")
                        .description("Protects your streak")
                        .itemType(ShopItemType.STREAK_SHIELD)
                        .priceGems(50)
                        .quantity(1)
                        .active(true)
                        .build();
        UserStats stats =
                UserStats.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .gems(100)
                        .streakShields(0)
                        .livesMode(LivesMode.LIMITED)
                        .currentLives(5)
                        .xpMultiplier(1.0)
                        .updatedAt(Instant.now())
                        .build();
        PurchaseRequest request = new PurchaseRequest(itemId);

        when(shopItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));
        when(purchaseRepository.save(any(Purchase.class)))
                .thenAnswer(
                        invocation -> {
                            Purchase p = invocation.getArgument(0);
                            p.setId(UUID.randomUUID());
                            return p;
                        });

        PurchaseResponse response = inventoryService.purchase(user, request);

        assertEquals(50, response.gemsSpent());
        assertEquals("SHIELD_1", response.item().code());
        assertEquals(50, stats.getGems());
        assertEquals(1, stats.getStreakShields());
        verify(shopItemRepository).findById(itemId);
        verify(userStatsRepository).findByUser(user);
        verify(userStatsRepository).save(stats);
        verify(purchaseRepository).save(any(Purchase.class));
    }

    @Test
    void purchase_withInsufficientGems_throwsException() {
        UUID itemId = UUID.randomUUID();
        ShopItem item =
                ShopItem.builder()
                        .id(itemId)
                        .code("SHIELD_1")
                        .title("Streak Shield")
                        .description("Protects your streak")
                        .itemType(ShopItemType.STREAK_SHIELD)
                        .priceGems(100)
                        .quantity(1)
                        .active(true)
                        .build();
        UserStats stats =
                UserStats.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .gems(50)
                        .streakShields(0)
                        .livesMode(LivesMode.LIMITED)
                        .currentLives(5)
                        .xpMultiplier(1.0)
                        .updatedAt(Instant.now())
                        .build();
        PurchaseRequest request = new PurchaseRequest(itemId);

        when(shopItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));

        assertThrows(InvalidInputException.class, () -> inventoryService.purchase(user, request));
    }

    @Test
    void purchase_withInactiveItem_throwsException() {
        UUID itemId = UUID.randomUUID();
        ShopItem item =
                ShopItem.builder()
                        .id(itemId)
                        .code("SHIELD_1")
                        .title("Streak Shield")
                        .description("Protects your streak")
                        .itemType(ShopItemType.STREAK_SHIELD)
                        .priceGems(50)
                        .quantity(1)
                        .active(false)
                        .build();
        PurchaseRequest request = new PurchaseRequest(itemId);

        when(shopItemRepository.findById(itemId)).thenReturn(Optional.of(item));

        assertThrows(InvalidInputException.class, () -> inventoryService.purchase(user, request));
    }

    @Test
    void purchase_itemNotFound_throwsNotFound() {
        UUID itemId = UUID.randomUUID();
        PurchaseRequest request = new PurchaseRequest(itemId);

        when(shopItemRepository.findById(itemId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> inventoryService.purchase(user, request));
    }

    @Test
    void purchase_userDisabled_throwsNotFound() {
        user.setEnabled(false);
        UUID itemId = UUID.randomUUID();
        PurchaseRequest request = new PurchaseRequest(itemId);

        assertThrows(NotFoundException.class, () -> inventoryService.purchase(user, request));
    }

    @Test
    void purchase_xpMultiplierItem_appliesEffectCorrectly() {
        UUID itemId = UUID.randomUUID();
        ShopItem item =
                ShopItem.builder()
                        .id(itemId)
                        .code("XP_MULT_2X")
                        .title("2x XP Multiplier")
                        .description("Doubles XP for 60 minutes")
                        .itemType(ShopItemType.XP_MULTIPLIER)
                        .priceGems(100)
                        .quantity(1)
                        .durationMinutes(60)
                        .multiplierValue(2.0)
                        .active(true)
                        .build();
        UserStats stats =
                UserStats.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .gems(100)
                        .streakShields(0)
                        .livesMode(LivesMode.LIMITED)
                        .currentLives(5)
                        .xpMultiplier(1.0)
                        .updatedAt(Instant.now())
                        .build();
        PurchaseRequest request = new PurchaseRequest(itemId);

        when(shopItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));
        when(purchaseRepository.save(any(Purchase.class)))
                .thenAnswer(
                        invocation -> {
                            Purchase p = invocation.getArgument(0);
                            p.setId(UUID.randomUUID());
                            return p;
                        });

        inventoryService.purchase(user, request);

        assertEquals(2.0, stats.getXpMultiplier());
        assertNotNull(stats.getXpMultiplierExpiresAt());
        assertTrue(stats.getXpMultiplierExpiresAt().isAfter(Instant.now()));
    }

    @Test
    void purchase_lifeItem_appliesEffectCorrectly() {
        UUID itemId = UUID.randomUUID();
        ShopItem item =
                ShopItem.builder()
                        .id(itemId)
                        .code("LIFE_1")
                        .title("Extra Life")
                        .description("Get one more life")
                        .itemType(ShopItemType.LIFE)
                        .priceGems(75)
                        .quantity(1)
                        .active(true)
                        .build();
        UserStats stats =
                UserStats.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .gems(100)
                        .streakShields(0)
                        .livesMode(LivesMode.LIMITED)
                        .currentLives(3)
                        .xpMultiplier(1.0)
                        .updatedAt(Instant.now())
                        .build();
        PurchaseRequest request = new PurchaseRequest(itemId);

        when(shopItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));
        when(purchaseRepository.save(any(Purchase.class)))
                .thenAnswer(
                        invocation -> {
                            Purchase p = invocation.getArgument(0);
                            p.setId(UUID.randomUUID());
                            return p;
                        });

        inventoryService.purchase(user, request);

        assertEquals(4, stats.getCurrentLives());
    }

    @Test
    void purchase_lifeItem_doesNotExceedMaxLives() {
        UUID itemId = UUID.randomUUID();
        ShopItem item =
                ShopItem.builder()
                        .id(itemId)
                        .code("LIFE_1")
                        .title("Extra Life")
                        .description("Get one more life")
                        .itemType(ShopItemType.LIFE)
                        .priceGems(75)
                        .quantity(1)
                        .active(true)
                        .build();
        UserStats stats =
                UserStats.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .gems(100)
                        .streakShields(0)
                        .livesMode(LivesMode.LIMITED)
                        .currentLives(5)
                        .xpMultiplier(1.0)
                        .updatedAt(Instant.now())
                        .build();
        PurchaseRequest request = new PurchaseRequest(itemId);

        when(shopItemRepository.findById(itemId)).thenReturn(Optional.of(item));
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));
        when(purchaseRepository.save(any(Purchase.class)))
                .thenAnswer(
                        invocation -> {
                            Purchase p = invocation.getArgument(0);
                            p.setId(UUID.randomUUID());
                            return p;
                        });

        inventoryService.purchase(user, request);

        assertEquals(5, stats.getCurrentLives());
    }
}
