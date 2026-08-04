package com.signasource.signa_api.gamification.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.gamification.dto.UserInventoryResponse;
import com.signasource.signa_api.gamification.entity.LivesMode;
import com.signasource.signa_api.gamification.service.InventoryService;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
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

        ResponseEntity<UserInventoryResponse> response = inventoryController.getMyInventory(userDetails);

        verify(inventoryService).getInventory(user);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }
}