package com.signasource.signa_api.gamification.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.gamification.dto.ChallengeClaimResponse;
import com.signasource.signa_api.gamification.dto.ChallengesResponse;
import com.signasource.signa_api.gamification.service.ChallengeService;
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
class ChallengeControllerTest {

    @Mock private ChallengeService challengeService;

    @InjectMocks private ChallengeController challengeController;

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
    void getChallenges_ReturnsTheUsersChallenges() {
        ChallengesResponse body =
                new ChallengesResponse(List.of(), false, List.of(), Instant.now());
        when(challengeService.getChallenges(user)).thenReturn(body);

        ResponseEntity<ChallengesResponse> response =
                challengeController.getChallenges(userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(body, response.getBody());
    }

    @Test
    void claim_DelegatesToTheService() {
        UUID id = UUID.randomUUID();
        ChallengeClaimResponse body = new ChallengeClaimResponse(null, 70);
        when(challengeService.claim(id, user)).thenReturn(body);

        ResponseEntity<ChallengeClaimResponse> response =
                challengeController.claim(id, userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(70, response.getBody().gems());
        verify(challengeService).claim(id, user);
    }
}
