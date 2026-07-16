package com.signasource.signa_api.users.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.service.FriendshipService;
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
}
