package com.signasource.signa_api.gamification.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.gamification.dto.AppliedEffectResponse;
import com.signasource.signa_api.gamification.dto.PurchaseRequest;
import com.signasource.signa_api.gamification.dto.PurchaseResponse;
import com.signasource.signa_api.gamification.dto.ShopItemResponse;
import com.signasource.signa_api.gamification.dto.UserInventoryResponse;
import com.signasource.signa_api.gamification.entity.LivesMode;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.service.PurchaseService;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
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
class PurchaseControllerTest {

    @Mock private PurchaseService purchaseService;

    @InjectMocks private PurchaseController purchaseController;

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
    void shouldCreatePurchaseForSelf() {
        UUID shopItemId = UUID.randomUUID();
        ShopItemResponse item =
                new ShopItemResponse(
                        shopItemId,
                        "streak_shield_x1",
                        "Escudo de racha x1",
                        "desc",
                        ShopItemType.STREAK_SHIELD,
                        40,
                        1,
                        null,
                        null,
                        true);
        PurchaseResponse expected =
                new PurchaseResponse(
                        UUID.randomUUID(),
                        item,
                        40,
                        Instant.now(),
                        new AppliedEffectResponse(
                                ShopItemType.STREAK_SHIELD, null, null, 1, null, null),
                        new UserInventoryResponse(
                                60, 1, LivesMode.LIMITED, 5, null, 1.0, null, false, null, false));
        when(purchaseService.purchaseForSelf(user, shopItemId)).thenReturn(expected);

        ResponseEntity<PurchaseResponse> response =
                purchaseController.purchaseForSelf(userDetails, new PurchaseRequest(shopItemId));

        verify(purchaseService).purchaseForSelf(user, shopItemId);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }
}
