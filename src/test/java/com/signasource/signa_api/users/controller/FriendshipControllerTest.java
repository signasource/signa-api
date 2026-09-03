package com.signasource.signa_api.users.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.users.dto.FriendEventResponse;
import com.signasource.signa_api.users.dto.FriendRequestResponse;
import com.signasource.signa_api.users.dto.FriendResponse;
import com.signasource.signa_api.users.dto.SentFriendRequestResponse;
import com.signasource.signa_api.users.entity.FriendEventType;
import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.FriendshipStatus;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.service.FriendEventService;
import com.signasource.signa_api.users.service.FriendshipService;
import java.time.Instant;
import java.time.LocalDateTime;
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
class FriendshipControllerTest {

    @Mock private FriendshipService friendshipService;

    @Mock private FriendEventService friendEventService;

    @InjectMocks private FriendshipController friendshipController;

    private CustomUserDetails mockUserDetails;
    private UUID currentUserId;
    private UUID otherUserId;
    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        currentUser = new User();
        currentUser.setId(currentUserId);

        mockUserDetails = new CustomUserDetails(currentUser);
    }

    @Test
    void sendFriendRequest_ReturnsCreated() {
        doNothing().when(friendshipService).sendFriendRequest(currentUser, otherUserId);

        ResponseEntity<Void> response =
                friendshipController.sendFriendRequest(mockUserDetails, otherUserId);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        verify(friendshipService).sendFriendRequest(currentUser, otherUserId);
    }

    @Test
    void acceptFriendRequest_ReturnsOk() {
        doNothing().when(friendshipService).acceptFriendRequest(otherUserId, currentUser);

        ResponseEntity<Void> response =
                friendshipController.acceptFriendRequest(mockUserDetails, otherUserId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(friendshipService).acceptFriendRequest(otherUserId, currentUser);
    }

    @Test
    void rejectFriendRequest_ReturnsOk() {
        doNothing().when(friendshipService).rejectFriendRequest(otherUserId, currentUser);

        ResponseEntity<Void> response =
                friendshipController.rejectFriendRequest(mockUserDetails, otherUserId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(friendshipService).rejectFriendRequest(otherUserId, currentUser);
    }

    @Test
    void blockUser_ReturnsOk() {
        doNothing().when(friendshipService).blockUser(currentUser, otherUserId);

        ResponseEntity<Void> response =
                friendshipController.blockUser(mockUserDetails, otherUserId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(friendshipService).blockUser(currentUser, otherUserId);
    }

    @Test
    void getFriends_ReturnsOkWithFriends() {
        User friend1 = new User();
        friend1.setId(UUID.randomUUID());
        friend1.setUsername("friend1");
        friend1.setName("Friend 1");

        User friend2 = new User();
        friend2.setId(UUID.randomUUID());
        friend2.setUsername("friend2");
        friend2.setName("Friend 2");

        FriendResponse response1 =
                new FriendResponse(
                        friend1.getId(),
                        friend1.getUsername(),
                        friend1.getName(),
                        LocalDateTime.now(),
                        7,
                        1200L,
                        30);

        FriendResponse response2 =
                new FriendResponse(
                        friend2.getId(),
                        friend2.getUsername(),
                        friend2.getName(),
                        LocalDateTime.now(),
                        0,
                        0L,
                        0);

        when(friendshipService.getFriendsWithStats(currentUser))
                .thenReturn(List.of(response1, response2));

        ResponseEntity<List<FriendResponse>> response =
                friendshipController.getFriends(mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        assertEquals(7, response.getBody().get(0).currentStreak());
        assertEquals(1200L, response.getBody().get(0).totalXp());
        verify(friendshipService).getFriendsWithStats(currentUser);
    }

    @Test
    void getFriends_ReturnsOkWithEmptyList() {
        when(friendshipService.getFriendsWithStats(currentUser)).thenReturn(List.of());

        ResponseEntity<List<FriendResponse>> response =
                friendshipController.getFriends(mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().size());
        verify(friendshipService).getFriendsWithStats(currentUser);
    }

    @Test
    void getPendingRequests_ReturnsOkWithRequests() {
        User requester1 = new User();
        requester1.setId(UUID.randomUUID());
        requester1.setUsername("requester1");
        requester1.setName("Requester 1");

        Friendship request1 = new Friendship();
        request1.setRequester(requester1);
        request1.setAddressee(currentUser);
        request1.setStatus(FriendshipStatus.PENDING);
        request1.setCreatedAt(LocalDateTime.now());

        when(friendshipService.getPendingRequests(currentUser)).thenReturn(List.of(request1));

        ResponseEntity<List<FriendRequestResponse>> response =
                friendshipController.getPendingRequests(mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        verify(friendshipService).getPendingRequests(currentUser);
    }

    @Test
    void removeFriend_ReturnsNoContent() {
        doNothing().when(friendshipService).removeFriend(currentUser, otherUserId);

        ResponseEntity<Void> response =
                friendshipController.removeFriend(mockUserDetails, otherUserId);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(friendshipService).removeFriend(currentUser, otherUserId);
    }

    @Test
    void unblockUser_ReturnsOk() {
        doNothing().when(friendshipService).unblockUser(currentUser, otherUserId);

        ResponseEntity<Void> response =
                friendshipController.unblockUser(mockUserDetails, otherUserId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(friendshipService).unblockUser(currentUser, otherUserId);
    }

    @Test
    void getFriendsEvents_ReturnsOkWithEvents() {
        FriendEventResponse event1 =
                new FriendEventResponse(
                        otherUserId,
                        "friend1",
                        "Friend 1",
                        FriendEventType.ACHIEVEMENT,
                        UUID.randomUUID(),
                        "Test",
                        "A test achievement",
                        false,
                        Instant.now());

        when(friendEventService.getFriendsEvents(currentUser, 50)).thenReturn(List.of(event1));

        ResponseEntity<List<FriendEventResponse>> response =
                friendshipController.getFriendsEvents(mockUserDetails, 50);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        verify(friendEventService).getFriendsEvents(currentUser, 50);
    }

    @Test
    void getFriendsEvents_ReturnsOkWithDefaultLimit() {
        when(friendEventService.getFriendsEvents(currentUser, 50)).thenReturn(List.of());

        ResponseEntity<List<FriendEventResponse>> response =
                friendshipController.getFriendsEvents(mockUserDetails, 50);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(friendEventService).getFriendsEvents(currentUser, 50);
    }

    @Test
    void cancelFriendRequest_ReturnsNoContent() {
        doNothing().when(friendshipService).cancelFriendRequest(currentUser, otherUserId);

        ResponseEntity<Void> response =
                friendshipController.cancelFriendRequest(mockUserDetails, otherUserId);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(friendshipService).cancelFriendRequest(currentUser, otherUserId);
    }

    @Test
    void getSentRequests_ReturnsOkWithRequests() {
        User addressee = new User();
        addressee.setId(UUID.randomUUID());
        addressee.setUsername("addressee1");
        addressee.setName("Addressee 1");

        Friendship request = new Friendship();
        request.setRequester(currentUser);
        request.setAddressee(addressee);
        request.setStatus(FriendshipStatus.PENDING);
        request.setCreatedAt(LocalDateTime.now());

        when(friendshipService.getSentRequests(currentUser)).thenReturn(List.of(request));

        ResponseEntity<List<SentFriendRequestResponse>> response =
                friendshipController.getSentRequests(mockUserDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("addressee1", response.getBody().get(0).addresseeUsername());
        verify(friendshipService).getSentRequests(currentUser);
    }

    @Test
    void likeEvent_ReturnsNoContent() {
        UUID refId = UUID.randomUUID();
        doNothing()
                .when(friendEventService)
                .likeEvent(currentUser, FriendEventType.ACHIEVEMENT, refId);

        ResponseEntity<Void> response =
                friendshipController.likeEvent(mockUserDetails, FriendEventType.ACHIEVEMENT, refId);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(friendEventService).likeEvent(currentUser, FriendEventType.ACHIEVEMENT, refId);
    }

    @Test
    void unlikeEvent_ReturnsNoContent() {
        UUID refId = UUID.randomUUID();
        doNothing()
                .when(friendEventService)
                .unlikeEvent(currentUser, FriendEventType.SIGN_LEARNED, refId);

        ResponseEntity<Void> response =
                friendshipController.unlikeEvent(
                        mockUserDetails, FriendEventType.SIGN_LEARNED, refId);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(friendEventService).unlikeEvent(currentUser, FriendEventType.SIGN_LEARNED, refId);
    }
}
