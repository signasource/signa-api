package com.signasource.signa_api.gamification.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.gamification.dto.AppliedEffectResponse;
import com.signasource.signa_api.gamification.dto.GiftClaimResponse;
import com.signasource.signa_api.gamification.dto.GiftResponse;
import com.signasource.signa_api.gamification.dto.SendGiftRequest;
import com.signasource.signa_api.gamification.dto.ShopItemResponse;
import com.signasource.signa_api.gamification.dto.UserInventoryResponse;
import com.signasource.signa_api.gamification.entity.GiftStatus;
import com.signasource.signa_api.gamification.entity.LivesMode;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.service.GiftService;
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
class GiftControllerTest {

    @Mock private GiftService giftService;

    @InjectMocks private GiftController giftController;

    private User user;
    private CustomUserDetails userDetails;
    private ShopItemResponse item;

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
        item =
                new ShopItemResponse(
                        UUID.randomUUID(),
                        "streak_shield_x1",
                        "Escudo de racha x1",
                        "desc",
                        ShopItemType.STREAK_SHIELD,
                        40,
                        1,
                        null,
                        null,
                        true);
    }

    @Test
    void shouldSendGift() {
        UUID recipientId = UUID.randomUUID();
        GiftResponse expected =
                new GiftResponse(
                        UUID.randomUUID(),
                        item,
                        user.getId(),
                        user.getUsername(),
                        recipientId,
                        "friend",
                        "hi",
                        GiftStatus.PENDING,
                        Instant.now(),
                        null,
                        Instant.now().plusSeconds(3600));
        SendGiftRequest request = new SendGiftRequest(item.id(), recipientId, "hi");
        when(giftService.sendGift(user, item.id(), recipientId, "hi")).thenReturn(expected);

        ResponseEntity<GiftResponse> response = giftController.sendGift(userDetails, request);

        verify(giftService).sendGift(user, item.id(), recipientId, "hi");
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }

    @Test
    void shouldReturnReceivedGifts() {
        GiftResponse gift =
                new GiftResponse(
                        UUID.randomUUID(),
                        item,
                        UUID.randomUUID(),
                        "friend",
                        user.getId(),
                        user.getUsername(),
                        "hi",
                        GiftStatus.PENDING,
                        Instant.now(),
                        null,
                        Instant.now().plusSeconds(3600));
        when(giftService.getReceivedGifts(user, null)).thenReturn(List.of(gift));

        ResponseEntity<List<GiftResponse>> response =
                giftController.getReceivedGifts(userDetails, null);

        verify(giftService).getReceivedGifts(user, null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void shouldReturnSentGifts() {
        GiftResponse gift =
                new GiftResponse(
                        UUID.randomUUID(),
                        item,
                        user.getId(),
                        user.getUsername(),
                        UUID.randomUUID(),
                        "friend",
                        "hi",
                        GiftStatus.PENDING,
                        Instant.now(),
                        null,
                        Instant.now().plusSeconds(3600));
        when(giftService.getSentGifts(user)).thenReturn(List.of(gift));

        ResponseEntity<List<GiftResponse>> response = giftController.getSentGifts(userDetails);

        verify(giftService).getSentGifts(user);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void shouldClaimGift() {
        UUID giftId = UUID.randomUUID();
        GiftClaimResponse expected =
                new GiftClaimResponse(
                        new GiftResponse(
                                giftId,
                                item,
                                UUID.randomUUID(),
                                "friend",
                                user.getId(),
                                user.getUsername(),
                                "hi",
                                GiftStatus.CLAIMED,
                                Instant.now(),
                                Instant.now(),
                                Instant.now().plusSeconds(3600)),
                        new AppliedEffectResponse(
                                ShopItemType.STREAK_SHIELD, null, null, 1, null, null),
                        new UserInventoryResponse(
                                10, 1, LivesMode.LIMITED, 5, null, 1.0, null, false, null, false, 0));
        when(giftService.claimGift(user, giftId)).thenReturn(expected);

        ResponseEntity<GiftClaimResponse> response = giftController.claimGift(giftId, userDetails);

        verify(giftService).claimGift(user, giftId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }
}
