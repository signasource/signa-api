package com.signasource.signa_api.users.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.users.service.FriendshipService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/friendships")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;

    @PostMapping("/request/{addresseeId}")
    public ResponseEntity<Void> sendFriendRequest(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable UUID addresseeId) {

        friendshipService.sendFriendRequest(currentUser.getUser().getId(), addresseeId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PutMapping("/accept/{requesterId}")
    public ResponseEntity<Void> acceptFriendRequest(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable UUID requesterId) {

        friendshipService.acceptFriendRequest(requesterId, currentUser.getUser().getId());
        return ResponseEntity.ok().build();
    }
}
