package com.signasource.signa_api.gamification.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.gamification.dto.PurchaseResponse;
import com.signasource.signa_api.gamification.dto.ShopItemResponse;
import com.signasource.signa_api.gamification.dto.UserInventoryResponse;
import com.signasource.signa_api.gamification.entity.LivesMode;
import com.signasource.signa_api.gamification.entity.PurchaseStatus;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.service.BoosterService;
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
class BoosterControllerTest {

    @Mock private BoosterService boosterService;

    @InjectMocks private BoosterController boosterController;

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
    void shouldActivateBooster() {
        UserInventoryResponse inventory =
                new UserInventoryResponse(
                        70, 1, LivesMode.LIMITED, 4, null, 1.0, null, false, null, false, 0);
        ShopItemResponse item =
                new ShopItemResponse(
                        UUID.randomUUID(),
                        "xp_boost",
                        "XP Boost",
                        "desc",
                        ShopItemType.XP_MULTIPLIER,
                        30,
                        1,
                        30,
                        2.0,
                        true);
        PurchaseResponse expected =
                new PurchaseResponse(
                        UUID.randomUUID(),
                        item,
                        30,
                        Instant.now(),
                        PurchaseStatus.ACTIVATED,
                        Instant.now(),
                        null,
                        inventory);
        when(boosterService.activate(user, ShopItemType.XP_MULTIPLIER)).thenReturn(expected);

        ResponseEntity<PurchaseResponse> response =
                boosterController.activate(ShopItemType.XP_MULTIPLIER, userDetails);

        verify(boosterService).activate(user, ShopItemType.XP_MULTIPLIER);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }
}
