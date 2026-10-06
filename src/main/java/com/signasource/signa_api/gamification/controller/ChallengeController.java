package com.signasource.signa_api.gamification.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.gamification.dto.ChallengeClaimResponse;
import com.signasource.signa_api.gamification.dto.ChallengesResponse;
import com.signasource.signa_api.gamification.service.ChallengeService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;

    @GetMapping
    public ResponseEntity<ChallengesResponse> getChallenges(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(challengeService.getChallenges(userDetails.getUser()));
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<ChallengeClaimResponse> claim(
            @PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(challengeService.claim(id, userDetails.getUser()));
    }
}
