package com.signasource.signa_api.gamification.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.gamification.dto.PurchaseRequest;
import com.signasource.signa_api.gamification.dto.PurchaseResponse;
import com.signasource.signa_api.gamification.dto.ShopItemResponse;
import com.signasource.signa_api.gamification.dto.UserInventoryResponse;
import com.signasource.signa_api.gamification.entity.LivesMode;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.service.InventoryService;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class InventoryControllerTest {

    @Mock private InventoryService inventoryService;

    @InjectMocks private InventoryController inventoryController;

    private User user;
    private CustomUserDetails userDetails;

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
        userDetails = new CustomUserDetails(user);
    }

    @Test
    void shouldReturnMyInventory() {
        UserInventoryResponse expected =
                new UserInventoryResponse(100, 1, LivesMode.LIMITED, 4, null, 1.0, null, false);
        when(inventoryService.getInventory(user)).thenReturn(expected);

        ResponseEntity<UserInventoryResponse> response =
                inventoryController.getMyInventory(userDetails);

        verify(inventoryService).getInventory(user);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }

    @Test
    void shouldReturnShopWithoutFilters() {
        UUID itemId = UUID.randomUUID();
        ShopItemResponse item =
                new ShopItemResponse(
                        itemId,
                        "SHIELD_1",
                        "Streak Shield",
                        "Protects your streak",
                        ShopItemType.STREAK_SHIELD,
                        50,
                        1,
                        null,
                        null);
        when(inventoryService.getShop(null, null)).thenReturn(List.of(item));

        ResponseEntity<List<ShopItemResponse>> response = inventoryController.getShop(null, null);

        verify(inventoryService).getShop(null, null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals(item, response.getBody().get(0));
    }

    @Test
    void shouldReturnShopWithItemTypeFilter() {
        UUID itemId = UUID.randomUUID();
        ShopItemResponse item =
                new ShopItemResponse(
                        itemId,
                        "SHIELD_1",
                        "Streak Shield",
                        "Protects your streak",
                        ShopItemType.STREAK_SHIELD,
                        50,
                        1,
                        null,
                        null);
        when(inventoryService.getShop(ShopItemType.STREAK_SHIELD, null)).thenReturn(List.of(item));

        ResponseEntity<List<ShopItemResponse>> response =
                inventoryController.getShop(ShopItemType.STREAK_SHIELD, null);

        verify(inventoryService).getShop(ShopItemType.STREAK_SHIELD, null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void shouldReturnShopWithPriceFilter() {
        UUID itemId = UUID.randomUUID();
        ShopItemResponse item =
                new ShopItemResponse(
                        itemId,
                        "SHIELD_1",
                        "Streak Shield",
                        "Protects your streak",
                        ShopItemType.STREAK_SHIELD,
                        50,
                        1,
                        null,
                        null);
        when(inventoryService.getShop(null, 50)).thenReturn(List.of(item));

        ResponseEntity<List<ShopItemResponse>> response = inventoryController.getShop(null, 50);

        verify(inventoryService).getShop(null, 50);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void shouldPurchaseItem() {
        UUID itemId = UUID.randomUUID();
        PurchaseRequest request = new PurchaseRequest(itemId);
        PurchaseResponse expected =
                new PurchaseResponse(
                        UUID.randomUUID(),
                        new ShopItemResponse(
                                itemId,
                                "SHIELD_1",
                                "Streak Shield",
                                "Protects your streak",
                                ShopItemType.STREAK_SHIELD,
                                50,
                                1,
                                null,
                                null),
                        50,
                        Instant.now());
        when(inventoryService.purchase(user, request)).thenReturn(expected);

        ResponseEntity<PurchaseResponse> response =
                inventoryController.purchase(userDetails, request);

        verify(inventoryService).purchase(user, request);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }
}
