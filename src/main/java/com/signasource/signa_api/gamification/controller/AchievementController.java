package com.signasource.signa_api.gamification.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.gamification.dto.AchievementResponse;
import com.signasource.signa_api.gamification.service.AchievementService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/achievements")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;

    @GetMapping
    public ResponseEntity<List<AchievementResponse>> getAchievements(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) Boolean unlocked,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(
                achievementService.getAchievements(userDetails.getUser(), unlocked, active));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AchievementResponse> getAchievementById(
            @PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(achievementService.getAchievementById(id, userDetails.getUser()));
    }
}
