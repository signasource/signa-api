package com.signasource.signa_api.gamification.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.gamification.dto.BoosterActivationResponse;
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
        userDetails = new CustomUserDetails(user);
        purchaseId = UUID.randomUUID();
    }

    @Test
    void shouldActivateBooster() {
        UserInventoryResponse inventory =
                new UserInventoryResponse(
                        70, 1, LivesMode.LIMITED, 4, null, null, false, 1.0, null, false);
        BoosterActivationResponse expected =
                new BoosterActivationResponse(
                        purchaseId,
                        "xp_boost",
                        ShopItemType.XP_MULTIPLIER,
                        PurchaseStatus.ACTIVATED,
                        Instant.now(),
                        inventory);
        when(boosterService.activate(user, purchaseId)).thenReturn(expected);

        ResponseEntity<BoosterActivationResponse> response =
                boosterController.activate(purchaseId, userDetails);

        verify(boosterService).activate(user, purchaseId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }
}
