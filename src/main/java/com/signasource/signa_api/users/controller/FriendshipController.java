package com.signasource.signa_api.users.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.users.dto.FriendEventResponse;
import com.signasource.signa_api.users.dto.FriendRequestResponse;
import com.signasource.signa_api.users.dto.FriendResponse;
import com.signasource.signa_api.users.dto.SentFriendRequestResponse;
import com.signasource.signa_api.users.entity.FriendEventType;
import com.signasource.signa_api.users.service.FriendEventService;
import com.signasource.signa_api.users.service.FriendshipService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/friendships")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;
    private final FriendEventService friendEventService;

    @PostMapping("/request/{addresseeId}")
    public ResponseEntity<Void> sendFriendRequest(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable UUID addresseeId) {

        friendshipService.sendFriendRequest(currentUser.getUser(), addresseeId);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PatchMapping("/accept/{requesterId}")
    public ResponseEntity<Void> acceptFriendRequest(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable UUID requesterId) {

        friendshipService.acceptFriendRequest(requesterId, currentUser.getUser());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/reject/{requesterId}")
    public ResponseEntity<Void> rejectFriendRequest(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable UUID requesterId) {

        friendshipService.rejectFriendRequest(requesterId, currentUser.getUser());
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/block/{userId}")
    public ResponseEntity<Void> blockUser(
            @AuthenticationPrincipal CustomUserDetails currentUser, @PathVariable UUID userId) {

        friendshipService.blockUser(currentUser.getUser(), userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    public ResponseEntity<List<FriendResponse>> getFriends(
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        return ResponseEntity.ok(friendshipService.getFriendsWithStats(currentUser.getUser()));
    }

    @GetMapping("/requests")
    public ResponseEntity<List<FriendRequestResponse>> getPendingRequests(
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        var requests =
                friendshipService.getPendingRequests(currentUser.getUser()).stream()
                        .map(FriendRequestResponse::from)
                        .toList();

        return ResponseEntity.ok(requests);
    }

    @DeleteMapping("/request/{addresseeId}")
    public ResponseEntity<Void> cancelFriendRequest(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable UUID addresseeId) {

        friendshipService.cancelFriendRequest(currentUser.getUser(), addresseeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/requests/sent")
    public ResponseEntity<List<SentFriendRequestResponse>> getSentRequests(
            @AuthenticationPrincipal CustomUserDetails currentUser) {

        var requests =
                friendshipService.getSentRequests(currentUser.getUser()).stream()
                        .map(SentFriendRequestResponse::from)
                        .toList();

        return ResponseEntity.ok(requests);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> removeFriend(
            @AuthenticationPrincipal CustomUserDetails currentUser, @PathVariable UUID userId) {

        friendshipService.removeFriend(currentUser.getUser(), userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/unblock/{userId}")
    public ResponseEntity<Void> unblockUser(
            @AuthenticationPrincipal CustomUserDetails currentUser, @PathVariable UUID userId) {

        friendshipService.unblockUser(currentUser.getUser(), userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/events")
    public ResponseEntity<List<FriendEventResponse>> getFriendsEvents(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam(defaultValue = "50") int limit) {

        var events = friendEventService.getFriendsEvents(currentUser.getUser(), limit);
        return ResponseEntity.ok(events);
    }

    @PostMapping("/events/{eventType}/{eventRefId}/like")
    public ResponseEntity<Void> likeEvent(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable FriendEventType eventType,
            @PathVariable UUID eventRefId) {

        friendEventService.likeEvent(currentUser.getUser(), eventType, eventRefId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/events/{eventType}/{eventRefId}/like")
    public ResponseEntity<Void> unlikeEvent(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PathVariable FriendEventType eventType,
            @PathVariable UUID eventRefId) {

        friendEventService.unlikeEvent(currentUser.getUser(), eventType, eventRefId);
        return ResponseEntity.noContent().build();
    }
}
