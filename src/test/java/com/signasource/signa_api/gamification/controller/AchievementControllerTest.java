package com.signasource.signa_api.gamification.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.gamification.dto.AchievementResponse;
import com.signasource.signa_api.gamification.entity.AchievementCriteriaType;
import com.signasource.signa_api.gamification.service.AchievementService;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
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
class AchievementControllerTest {

    @Mock private AchievementService achievementService;

    @InjectMocks private AchievementController achievementController;

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
    void shouldReturnAchievementsList() {
        AchievementResponse achievement =
                new AchievementResponse(
                        UUID.randomUUID(),
                        "STREAK_7",
                        "Racha de 7 días",
                        "desc",
                        null,
                        AchievementCriteriaType.STREAK_DAYS,
                        7,
                        true,
                        false,
                        null);
        when(achievementService.getAchievements(user, null, null)).thenReturn(List.of(achievement));

        ResponseEntity<List<AchievementResponse>> response =
                achievementController.getAchievements(userDetails, null, null);

        verify(achievementService).getAchievements(user, null, null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
    }

    @Test
    void shouldReturnAchievementById() {
        UUID id = UUID.randomUUID();
        AchievementResponse achievement =
                new AchievementResponse(
                        id,
                        "STREAK_7",
                        "Racha de 7 días",
                        "desc",
                        null,
                        AchievementCriteriaType.STREAK_DAYS,
                        7,
                        true,
                        true,
                        java.time.Instant.now());
        when(achievementService.getAchievementById(id, user)).thenReturn(achievement);

        ResponseEntity<AchievementResponse> response =
                achievementController.getAchievementById(id, userDetails);

        verify(achievementService).getAchievementById(id, user);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(achievement, response.getBody());
    }
}
