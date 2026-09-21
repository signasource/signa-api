package com.signasource.signa_api.users.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.users.dto.WeeklyRankingResponse;
import com.signasource.signa_api.users.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ranking")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    /** Top 100 users this week by XP, plus the caller's position. */
    @GetMapping("/global")
    public ResponseEntity<WeeklyRankingResponse> getGlobal(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(rankingService.getGlobalRanking(userDetails.getUser()));
    }

    /** The caller's accepted friends ranked by XP this week, plus the caller's position. */
    @GetMapping("/friends")
    public ResponseEntity<WeeklyRankingResponse> getFriends(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(rankingService.getFriendsRanking(userDetails.getUser()));
    }
}
