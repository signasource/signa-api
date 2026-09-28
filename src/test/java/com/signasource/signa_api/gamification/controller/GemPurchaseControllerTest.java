package com.signasource.signa_api.gamification.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.gamification.dto.GemPackResponse;
import com.signasource.signa_api.gamification.dto.GemPurchaseResponse;
import com.signasource.signa_api.gamification.dto.RedeemGemPurchaseRequest;
import com.signasource.signa_api.gamification.dto.UserInventoryResponse;
import com.signasource.signa_api.gamification.entity.LivesMode;
import com.signasource.signa_api.gamification.service.GemPurchaseService;
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
class GemPurchaseControllerTest {

    @Mock private GemPurchaseService gemPurchaseService;

    @InjectMocks private GemPurchaseController controller;

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
    void shouldListGemPacks() {
        List<GemPackResponse> packs =
                List.of(new GemPackResponse(UUID.randomUUID(), "gems_pack_120", 120, 1));
        when(gemPurchaseService.getPacks()).thenReturn(packs);

        ResponseEntity<List<GemPackResponse>> response = controller.getPacks();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(packs, response.getBody());
    }

    @Test
    void shouldRedeemPurchaseWithCreatedStatus() {
        UserInventoryResponse inventory =
                new UserInventoryResponse(
                        400, 0, LivesMode.LIMITED, 5, null, 1.0, null, false, null, false, 0);
        GemPurchaseResponse expected =
                new GemPurchaseResponse(
                        UUID.randomUUID(),
                        "gems_pack_300",
                        300,
                        "GPA.1234",
                        Instant.now(),
                        false,
                        inventory);
        when(gemPurchaseService.redeem(user, "gems_pack_300", "token")).thenReturn(expected);

        ResponseEntity<GemPurchaseResponse> response =
                controller.redeem(
                        userDetails, new RedeemGemPurchaseRequest("gems_pack_300", "token"));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(expected, response.getBody());
        verify(gemPurchaseService).redeem(user, "gems_pack_300", "token");
    }
}
