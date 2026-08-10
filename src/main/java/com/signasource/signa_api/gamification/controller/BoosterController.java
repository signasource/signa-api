package com.signasource.signa_api.gamification.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.gamification.dto.BoosterActivationResponse;
import com.signasource.signa_api.gamification.service.BoosterService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/boosters")
@RequiredArgsConstructor
public class BoosterController {

    private final BoosterService boosterService;

    @PostMapping("/{purchaseId}/activate")
    public ResponseEntity<BoosterActivationResponse> activate(
            @PathVariable UUID purchaseId, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(boosterService.activate(userDetails.getUser(), purchaseId));
    }
}
