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
import java.util.List;
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
        when(userRepository.findById(addresseeId)).thenReturn(Optional.of(addressee));
        when(friendshipRepository.findFriendshipBetween(requester, addressee))
                .thenReturn(Optional.empty());

        friendshipService.sendFriendRequest(requester, addresseeId);

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
                () -> friendshipService.sendFriendRequest(requester, requesterId));
        verifyNoInteractions(userRepository, friendshipRepository);
    }

    @Test
    void sendFriendRequest_ThrowsNotFoundException_WhenAddresseeNotFound() {
        when(userRepository.findById(addresseeId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> friendshipService.sendFriendRequest(requester, addresseeId));
    }

    @Test
    void sendFriendRequest_ThrowsResourceAlreadyInUseException_WhenFriendshipExists() {
        Friendship existingFriendship = new Friendship();
        existingFriendship.setStatus(FriendshipStatus.PENDING);

        when(userRepository.findById(addresseeId)).thenReturn(Optional.of(addressee));
        when(friendshipRepository.findFriendshipBetween(requester, addressee))
                .thenReturn(Optional.of(existingFriendship));

        assertThrows(
                ResourceAlreadyInUseException.class,
                () -> friendshipService.sendFriendRequest(requester, addresseeId));
    }

    @Test
    void sendFriendRequest_ThrowsResourceAlreadyInUseException_WhenRelationIsBlocked() {
        Friendship blockedFriendship = new Friendship();
        blockedFriendship.setStatus(FriendshipStatus.BLOCKED);

        when(userRepository.findById(addresseeId)).thenReturn(Optional.of(addressee));
        when(friendshipRepository.findFriendshipBetween(requester, addressee))
                .thenReturn(Optional.of(blockedFriendship));

        assertThrows(
                ResourceAlreadyInUseException.class,
                () -> friendshipService.sendFriendRequest(requester, addresseeId));

        verify(friendshipRepository, never()).save(any(Friendship.class));
    }

    @Test
    void sendFriendRequest_UpdatesExistingRecord_WhenStatusIsRejected() {
        Friendship rejectedFriendship = new Friendship();
        rejectedFriendship.setRequester(addressee);
        rejectedFriendship.setAddressee(requester);
        rejectedFriendship.setStatus(FriendshipStatus.REJECTED);

        when(userRepository.findById(addresseeId)).thenReturn(Optional.of(addressee));
        when(friendshipRepository.findFriendshipBetween(requester, addressee))
                .thenReturn(Optional.of(rejectedFriendship));

        friendshipService.sendFriendRequest(requester, addresseeId);

        ArgumentCaptor<Friendship> friendshipCaptor = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipRepository, times(1)).save(friendshipCaptor.capture());

        Friendship savedFriendship = friendshipCaptor.getValue();
        assertEquals(requester, savedFriendship.getRequester());
        assertEquals(addressee, savedFriendship.getAddressee());
        assertEquals(FriendshipStatus.PENDING, savedFriendship.getStatus());
    }

    @Test
    void acceptFriendRequest_Success() {
        Friendship pendingFriendship = new Friendship();
        pendingFriendship.setRequester(requester);
        pendingFriendship.setAddressee(addressee);
        pendingFriendship.setStatus(FriendshipStatus.PENDING);

        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(friendshipRepository.findByRequesterAndAddressee(requester, addressee))
                .thenReturn(Optional.of(pendingFriendship));

        friendshipService.acceptFriendRequest(requesterId, addressee);

        verify(friendshipRepository, times(1)).save(pendingFriendship);
        assertEquals(FriendshipStatus.ACCEPTED, pendingFriendship.getStatus());
    }

    @Test
    void acceptFriendRequest_ThrowsNotFoundException_WhenRequesterNotFound() {
        when(userRepository.findById(requesterId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> friendshipService.acceptFriendRequest(requesterId, addressee));
    }

    @Test
    void acceptFriendRequest_ThrowsNotFoundException_WhenFriendshipNotFound() {
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(friendshipRepository.findByRequesterAndAddressee(requester, addressee))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> friendshipService.acceptFriendRequest(requesterId, addressee));
    }

    @Test
    void acceptFriendRequest_ThrowsInvalidInputException_WhenStatusIsNotPending() {
        Friendship nonPendingFriendship = new Friendship();
        nonPendingFriendship.setRequester(requester);
        nonPendingFriendship.setAddressee(addressee);
        nonPendingFriendship.setStatus(FriendshipStatus.ACCEPTED);

        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(friendshipRepository.findByRequesterAndAddressee(requester, addressee))
                .thenReturn(Optional.of(nonPendingFriendship));

        assertThrows(
                InvalidInputException.class,
                () -> friendshipService.acceptFriendRequest(requesterId, addressee));

        verify(friendshipRepository, never()).save(any(Friendship.class));
    }

    @Test
    void rejectFriendRequest_Success() {
        Friendship pendingFriendship = new Friendship();
        pendingFriendship.setRequester(requester);
        pendingFriendship.setAddressee(addressee);
        pendingFriendship.setStatus(FriendshipStatus.PENDING);

        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(friendshipRepository.findByRequesterAndAddressee(requester, addressee))
                .thenReturn(Optional.of(pendingFriendship));

        friendshipService.rejectFriendRequest(requesterId, addressee);

        verify(friendshipRepository, times(1)).save(pendingFriendship);
        assertEquals(FriendshipStatus.REJECTED, pendingFriendship.getStatus());
    }

    @Test
    void rejectFriendRequest_ThrowsNotFoundException_WhenRequesterNotFound() {
        when(userRepository.findById(requesterId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> friendshipService.rejectFriendRequest(requesterId, addressee));
    }

    @Test
    void rejectFriendRequest_ThrowsNotFoundException_WhenFriendshipNotFound() {
        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(friendshipRepository.findByRequesterAndAddressee(requester, addressee))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> friendshipService.rejectFriendRequest(requesterId, addressee));
    }

    @Test
    void rejectFriendRequest_ThrowsInvalidInputException_WhenStatusIsNotPending() {
        Friendship nonPendingFriendship = new Friendship();
        nonPendingFriendship.setRequester(requester);
        nonPendingFriendship.setAddressee(addressee);
        nonPendingFriendship.setStatus(FriendshipStatus.ACCEPTED);

        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(friendshipRepository.findByRequesterAndAddressee(requester, addressee))
                .thenReturn(Optional.of(nonPendingFriendship));

        assertThrows(
                InvalidInputException.class,
                () -> friendshipService.rejectFriendRequest(requesterId, addressee));

        verify(friendshipRepository, never()).save(any(Friendship.class));
    }

    @Test
    void rejectFriendRequest_ThrowsInvalidInputException_WhenStatusIsBlocked() {
        Friendship blockedFriendship = new Friendship();
        blockedFriendship.setRequester(requester);
        blockedFriendship.setAddressee(addressee);
        blockedFriendship.setStatus(FriendshipStatus.BLOCKED);

        when(userRepository.findById(requesterId)).thenReturn(Optional.of(requester));
        when(friendshipRepository.findByRequesterAndAddressee(requester, addressee))
                .thenReturn(Optional.of(blockedFriendship));

        assertThrows(
                InvalidInputException.class,
                () -> friendshipService.rejectFriendRequest(requesterId, addressee));

        verify(friendshipRepository, never()).save(any(Friendship.class));
    }

    @Test
    void blockUser_CreatesNewBlockedFriendship_WhenNoRelationExists() {
        when(userRepository.findById(addresseeId)).thenReturn(Optional.of(addressee));
        when(friendshipRepository.findFriendshipBetween(requester, addressee))
                .thenReturn(Optional.empty());

        friendshipService.blockUser(requester, addresseeId);

        ArgumentCaptor<Friendship> friendshipCaptor = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipRepository, times(1)).save(friendshipCaptor.capture());

        Friendship savedFriendship = friendshipCaptor.getValue();
        assertEquals(requester, savedFriendship.getRequester());
        assertEquals(addressee, savedFriendship.getAddressee());
        assertEquals(FriendshipStatus.BLOCKED, savedFriendship.getStatus());
    }

    @Test
    void blockUser_UpdatesExistingRelation_RegardlessOfPriorStatus() {
        Friendship acceptedFriendship = new Friendship();
        acceptedFriendship.setRequester(addressee);
        acceptedFriendship.setAddressee(requester);
        acceptedFriendship.setStatus(FriendshipStatus.ACCEPTED);

        when(userRepository.findById(addresseeId)).thenReturn(Optional.of(addressee));
        when(friendshipRepository.findFriendshipBetween(requester, addressee))
                .thenReturn(Optional.of(acceptedFriendship));

        friendshipService.blockUser(requester, addresseeId);

        ArgumentCaptor<Friendship> friendshipCaptor = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipRepository, times(1)).save(friendshipCaptor.capture());

        Friendship savedFriendship = friendshipCaptor.getValue();
        assertEquals(requester, savedFriendship.getRequester());
        assertEquals(addressee, savedFriendship.getAddressee());
        assertEquals(FriendshipStatus.BLOCKED, savedFriendship.getStatus());
    }

    @Test
    void blockUser_ThrowsResourceAlreadyInUseException_WhenAlreadyBlockedByCaller() {
        Friendship blockedFriendship = new Friendship();
        blockedFriendship.setRequester(requester);
        blockedFriendship.setAddressee(addressee);
        blockedFriendship.setStatus(FriendshipStatus.BLOCKED);

        when(userRepository.findById(addresseeId)).thenReturn(Optional.of(addressee));
        when(friendshipRepository.findFriendshipBetween(requester, addressee))
                .thenReturn(Optional.of(blockedFriendship));

        assertThrows(
                ResourceAlreadyInUseException.class,
                () -> friendshipService.blockUser(requester, addresseeId));

        verify(friendshipRepository, never()).save(any(Friendship.class));
    }

    @Test
    void blockUser_ThrowsResourceAlreadyInUseException_WhenAlreadyBlockedByOtherParty() {
        Friendship blockedFriendship = new Friendship();
        blockedFriendship.setRequester(addressee);
        blockedFriendship.setAddressee(requester);
        blockedFriendship.setStatus(FriendshipStatus.BLOCKED);

        when(userRepository.findById(addresseeId)).thenReturn(Optional.of(addressee));
        when(friendshipRepository.findFriendshipBetween(requester, addressee))
                .thenReturn(Optional.of(blockedFriendship));

        assertThrows(
                ResourceAlreadyInUseException.class,
                () -> friendshipService.blockUser(requester, addresseeId));

        verify(friendshipRepository, never()).save(any(Friendship.class));
    }

    @Test
    void blockUser_ThrowsInvalidInputException_WhenSameUser() {
        assertThrows(
                InvalidInputException.class,
                () -> friendshipService.blockUser(requester, requesterId));
        verifyNoInteractions(userRepository, friendshipRepository);
    }

    @Test
    void blockUser_ThrowsNotFoundException_WhenBlockedUserNotFound() {
        when(userRepository.findById(addresseeId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class, () -> friendshipService.blockUser(requester, addresseeId));
    }

    @Test
    void getFriends_ReturnsAcceptedFriendships() {
        Friendship friendship1 = new Friendship();
        friendship1.setStatus(FriendshipStatus.ACCEPTED);

        Friendship friendship2 = new Friendship();
        friendship2.setStatus(FriendshipStatus.ACCEPTED);

        when(friendshipRepository.findAllFriendshipsByUserAndStatus(
                        requester, FriendshipStatus.ACCEPTED))
                .thenReturn(List.of(friendship1, friendship2));

        List<Friendship> friends = friendshipService.getFriends(requester);

        assertEquals(2, friends.size());
        verify(friendshipRepository, times(1))
                .findAllFriendshipsByUserAndStatus(requester, FriendshipStatus.ACCEPTED);
    }

    @Test
    void getFriends_ReturnsEmptyList_WhenNoFriends() {
        when(friendshipRepository.findAllFriendshipsByUserAndStatus(
                        requester, FriendshipStatus.ACCEPTED))
                .thenReturn(List.of());

        List<Friendship> friends = friendshipService.getFriends(requester);

        assertEquals(0, friends.size());
    }

    @Test
    void getPendingRequests_ReturnsPendingRequests() {
        Friendship request1 = new Friendship();
        request1.setStatus(FriendshipStatus.PENDING);

        Friendship request2 = new Friendship();
        request2.setStatus(FriendshipStatus.PENDING);

        when(friendshipRepository.findByAddresseeAndStatus(addressee, FriendshipStatus.PENDING))
                .thenReturn(List.of(request1, request2));

        List<Friendship> requests = friendshipService.getPendingRequests(addressee);

        assertEquals(2, requests.size());
        verify(friendshipRepository, times(1))
                .findByAddresseeAndStatus(addressee, FriendshipStatus.PENDING);
    }

    @Test
    void getPendingRequests_ReturnsEmptyList_WhenNoPendingRequests() {
        when(friendshipRepository.findByAddresseeAndStatus(addressee, FriendshipStatus.PENDING))
                .thenReturn(List.of());

        List<Friendship> requests = friendshipService.getPendingRequests(addressee);

        assertEquals(0, requests.size());
    }

    @Test
    void removeFriend_Success() {
        Friendship acceptedFriendship = new Friendship();
        acceptedFriendship.setStatus(FriendshipStatus.ACCEPTED);

        when(userRepository.findById(addresseeId)).thenReturn(Optional.of(addressee));
        when(friendshipRepository.findFriendshipBetween(requester, addressee))
                .thenReturn(Optional.of(acceptedFriendship));

        friendshipService.removeFriend(requester, addresseeId);

        verify(friendshipRepository, times(1)).delete(acceptedFriendship);
    }

    @Test
    void removeFriend_ThrowsInvalidInputException_WhenSameUser() {
        assertThrows(
                InvalidInputException.class,
                () -> friendshipService.removeFriend(requester, requesterId));
        verifyNoInteractions(userRepository, friendshipRepository);
    }

    @Test
    void removeFriend_ThrowsNotFoundException_WhenFriendNotFound() {
        when(userRepository.findById(addresseeId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> friendshipService.removeFriend(requester, addresseeId));
    }

    @Test
    void removeFriend_ThrowsNotFoundException_WhenFriendshipNotFound() {
        when(userRepository.findById(addresseeId)).thenReturn(Optional.of(addressee));
        when(friendshipRepository.findFriendshipBetween(requester, addressee))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> friendshipService.removeFriend(requester, addresseeId));
    }

    @Test
    void removeFriend_ThrowsInvalidInputException_WhenStatusIsNotAccepted() {
        Friendship pendingFriendship = new Friendship();
        pendingFriendship.setStatus(FriendshipStatus.PENDING);

        when(userRepository.findById(addresseeId)).thenReturn(Optional.of(addressee));
        when(friendshipRepository.findFriendshipBetween(requester, addressee))
                .thenReturn(Optional.of(pendingFriendship));

        assertThrows(
                InvalidInputException.class,
                () -> friendshipService.removeFriend(requester, addresseeId));

        verify(friendshipRepository, never()).delete(any(Friendship.class));
    }

    @Test
    void unblockUser_Success() {
        Friendship blockedFriendship = new Friendship();
        blockedFriendship.setRequester(requester);
        blockedFriendship.setAddressee(addressee);
        blockedFriendship.setStatus(FriendshipStatus.BLOCKED);

        when(userRepository.findById(addresseeId)).thenReturn(Optional.of(addressee));
        when(friendshipRepository.findByRequesterAndAddressee(requester, addressee))
                .thenReturn(Optional.of(blockedFriendship));

        friendshipService.unblockUser(requester, addresseeId);

        verify(friendshipRepository, times(1)).delete(blockedFriendship);
    }

    @Test
    void unblockUser_ThrowsInvalidInputException_WhenSameUser() {
        assertThrows(
                InvalidInputException.class,
                () -> friendshipService.unblockUser(requester, requesterId));
        verifyNoInteractions(userRepository, friendshipRepository);
    }

    @Test
    void unblockUser_ThrowsNotFoundException_WhenUserNotFound() {
        when(userRepository.findById(addresseeId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> friendshipService.unblockUser(requester, addresseeId));
    }

    @Test
    void unblockUser_ThrowsNotFoundException_WhenBlockingNotFound() {
        when(userRepository.findById(addresseeId)).thenReturn(Optional.of(addressee));
        when(friendshipRepository.findByRequesterAndAddressee(requester, addressee))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> friendshipService.unblockUser(requester, addresseeId));
    }

    @Test
    void unblockUser_ThrowsInvalidInputException_WhenStatusIsNotBlocked() {
        Friendship acceptedFriendship = new Friendship();
        acceptedFriendship.setRequester(requester);
        acceptedFriendship.setAddressee(addressee);
        acceptedFriendship.setStatus(FriendshipStatus.ACCEPTED);

        when(userRepository.findById(addresseeId)).thenReturn(Optional.of(addressee));
        when(friendshipRepository.findByRequesterAndAddressee(requester, addressee))
                .thenReturn(Optional.of(acceptedFriendship));

        assertThrows(
                InvalidInputException.class,
                () -> friendshipService.unblockUser(requester, addresseeId));

        verify(friendshipRepository, never()).delete(any(Friendship.class));
    }
}
