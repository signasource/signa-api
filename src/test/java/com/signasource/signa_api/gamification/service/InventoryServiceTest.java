package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.UserInventoryResponse;
import com.signasource.signa_api.gamification.entity.LivesMode;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    void getInventory_whenUnlimitedLivesActive_returnsInfiniteEffectiveMode() {
        UserStats stats =
                UserStats.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .gems(0)
                        .streakShields(0)
                        .livesMode(LivesMode.INFINITE)
                        .currentLives(2)
                        .unlimitedLivesExpiresAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                        .xpMultiplier(1.0)
                        .updatedAt(Instant.now())
                        .build();
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));

        UserInventoryResponse response = inventoryService.getInventory(user);

        assertEquals(LivesMode.INFINITE, response.livesMode());
        assertTrue(response.unlimitedLivesActive());
        assertNotNull(response.unlimitedLivesExpiresAt());
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
}
