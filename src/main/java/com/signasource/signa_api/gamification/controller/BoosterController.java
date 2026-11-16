package com.signasource.signa_api.gamification.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.gamification.dto.PurchaseResponse;
import com.signasource.signa_api.gamification.dto.StoredBoostersResponse;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.service.BoosterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/boosters")
@RequiredArgsConstructor
public class BoosterController {

    private final BoosterService boosterService;

    /** Boosters bought but not yet activated. */
    @GetMapping
    public ResponseEntity<StoredBoostersResponse> getStored(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(boosterService.getStored(userDetails.getUser()));
    }

    @PostMapping("/{itemType}/activate")
    public ResponseEntity<PurchaseResponse> activate(
            @PathVariable ShopItemType itemType,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(boosterService.activate(userDetails.getUser(), itemType));
    }
}
