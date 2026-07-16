package com.signasource.signa_api.users.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.FriendshipStatus;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.FriendshipRepository;
import com.signasource.signa_api.users.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

    @Mock private FriendshipRepository friendshipRepository;

    @Mock private UserRepository userRepository;

    @InjectMocks private FriendshipService friendshipService;

    private User requester;
    private User addressee;
    private UUID requesterId;
    private UUID addresseeId;

    @BeforeEach
    void setUp() {
        requesterId = UUID.randomUUID();
        addresseeId = UUID.randomUUID();

        requester = new User();
        requester.setId(requesterId);

        addressee = new User();
        addressee.setId(addresseeId);
    }

    @Test
    void sendFriendRequest_Success() {
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(userRepository.findById(addresseeId)).thenReturn(Optional.of(addressee));
        when(friendshipRepository.friendshipExists(requester, addressee)).thenReturn(false);

        friendshipService.sendFriendRequest(requesterId, addresseeId);

        ArgumentCaptor<Friendship> friendshipCaptor = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipRepository, times(1)).save(friendshipCaptor.capture());

        Friendship savedFriendship = friendshipCaptor.getValue();
        assertEquals(requester, savedFriendship.getRequester());
        assertEquals(addressee, savedFriendship.getAddressee());
        assertEquals(FriendshipStatus.PENDING, savedFriendship.getStatus());
    }

    @Test
    void sendFriendRequest_ThrowsInvalidInputException_WhenSameUser() {
        assertThrows(
                InvalidInputException.class,
                () -> friendshipService.sendFriendRequest(requesterId, requesterId));
        verifyNoInteractions(userRepository, friendshipRepository);
    }

    @Test
    void sendFriendRequest_ThrowsNotFoundException_WhenRequesterNotFound() {
        when(userRepository.findById(requesterId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> friendshipService.sendFriendRequest(requesterId, addresseeId));
    }

    @Test
    void sendFriendRequest_ThrowsResourceAlreadyInUseException_WhenFriendshipExists() {
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(userRepository.findById(addresseeId)).thenReturn(Optional.of(addressee));
        when(friendshipRepository.friendshipExists(requester, addressee)).thenReturn(true);

        assertThrows(
                ResourceAlreadyInUseException.class,
                () -> friendshipService.sendFriendRequest(requesterId, addresseeId));
    }

    @Test
    void acceptFriendRequest_Success() {
        Friendship pendingFriendship = new Friendship();
        pendingFriendship.setRequester(requester);
        pendingFriendship.setAddressee(addressee);
        pendingFriendship.setStatus(FriendshipStatus.PENDING);

        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(userRepository.findById(addresseeId)).thenReturn(Optional.of(addressee));
        when(friendshipRepository.findByRequesterAndAddressee(requester, addressee))
                .thenReturn(Optional.of(pendingFriendship));

        friendshipService.acceptFriendRequest(requesterId, addresseeId);

        verify(friendshipRepository, times(1)).save(pendingFriendship);
        assertEquals(FriendshipStatus.ACCEPTED, pendingFriendship.getStatus());
    }

    @Test
    void acceptFriendRequest_ThrowsNotFoundException_WhenFriendshipNotFound() {
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(userRepository.findById(addresseeId)).thenReturn(Optional.of(addressee));
        when(friendshipRepository.findByRequesterAndAddressee(requester, addressee))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> friendshipService.acceptFriendRequest(requesterId, addresseeId));
    }

    @Test
    void acceptFriendRequest_ThrowsInvalidInputException_WhenStatusIsNotPending() {
        Friendship nonPendingFriendship = new Friendship();
        nonPendingFriendship.setRequester(requester);
        nonPendingFriendship.setAddressee(addressee);
        nonPendingFriendship.setStatus(FriendshipStatus.ACCEPTED);

        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(userRepository.findById(addresseeId)).thenReturn(Optional.of(addressee));
        when(friendshipRepository.findByRequesterAndAddressee(requester, addressee))
                .thenReturn(Optional.of(nonPendingFriendship));

        assertThrows(
                InvalidInputException.class,
                () -> friendshipService.acceptFriendRequest(requesterId, addresseeId));

        verify(friendshipRepository, never()).save(any(Friendship.class));
    }
}
