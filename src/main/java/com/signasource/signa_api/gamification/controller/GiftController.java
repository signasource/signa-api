package com.signasource.signa_api.gamification.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.gamification.dto.GiftClaimResponse;
import com.signasource.signa_api.gamification.dto.GiftResponse;
import com.signasource.signa_api.gamification.dto.SendGiftRequest;
import com.signasource.signa_api.gamification.entity.GiftStatus;
import com.signasource.signa_api.gamification.service.GiftService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/store/gifts")
@RequiredArgsConstructor
public class GiftController {

    private final GiftService giftService;

    @PostMapping
    public ResponseEntity<GiftResponse> sendGift(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody SendGiftRequest request) {
        GiftResponse response =
                giftService.sendGift(
                        userDetails.getUser(),
                        request.shopItemId(),
                        request.recipientUserId(),
                        request.message());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/received")
    public ResponseEntity<List<GiftResponse>> getReceivedGifts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(required = false) GiftStatus status) {
        return ResponseEntity.ok(giftService.getReceivedGifts(userDetails.getUser(), status));
    }

    @GetMapping("/sent")
    public ResponseEntity<List<GiftResponse>> getSentGifts(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(giftService.getSentGifts(userDetails.getUser()));
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<GiftClaimResponse> claimGift(
            @PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(giftService.claimGift(userDetails.getUser(), id));
    }
}
