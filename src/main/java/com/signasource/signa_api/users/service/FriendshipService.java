package com.signasource.signa_api.users.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.notification.entity.NotificationCode;
import com.signasource.signa_api.notification.service.NotificationService;
import com.signasource.signa_api.users.dto.FriendResponse;
import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.FriendshipStatus;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.FriendshipRepository;
import com.signasource.signa_api.users.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;
    private final NotificationService notificationService;

    @Transactional
    public void sendFriendRequest(User requester, UUID addresseeId) {
        if (requester.getId().equals(addresseeId)) {
            throw new InvalidInputException("You can't send a friend request to yourself");
        }

        User addressee =
                userRepository
                        .findById(addresseeId)
                        .orElseThrow(() -> new NotFoundException("Destination user not found"));

        Optional<Friendship> existingRelation =
                friendshipRepository.findFriendshipBetween(requester, addressee);

        if (existingRelation.isPresent()) {
            Friendship friendship = existingRelation.get();

            if (friendship.getStatus() == FriendshipStatus.REJECTED) {
                friendship.setRequester(requester);
                friendship.setAddressee(addressee);
                friendship.setStatus(FriendshipStatus.PENDING);
                friendshipRepository.save(friendship);
                notifyRequestReceived(addressee, requester);
                return;
            }

            if (friendship.getStatus() == FriendshipStatus.BLOCKED) {
                throw new ResourceAlreadyInUseException(
                        "Cannot send a friend request to this user.");
            }

            throw new ResourceAlreadyInUseException("The relationship or request already exists.");
        }

        Friendship newFriendship =
                Friendship.builder()
                        .requester(requester)
                        .addressee(addressee)
                        .status(FriendshipStatus.PENDING)
                        .build();

        friendshipRepository.save(newFriendship);
        notifyRequestReceived(addressee, requester);
    }

    @Transactional
    public void acceptFriendRequest(UUID requesterId, User addressee) {
        User requester =
                userRepository
                        .findById(requesterId)
                        .orElseThrow(() -> new NotFoundException("Origin user not found."));

        Friendship friendship =
                friendshipRepository
                        .findByRequesterAndAddressee(requester, addressee)
                        .orElseThrow(() -> new NotFoundException("Request not found."));

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new InvalidInputException("Only pending requests can be accepted.");
        }

        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendshipRepository.save(friendship);
        notifyRequestAccepted(requester, addressee);
    }

    @Transactional
    public void rejectFriendRequest(UUID requesterId, User addressee) {
        User requester =
                userRepository
                        .findById(requesterId)
                        .orElseThrow(() -> new NotFoundException("Origin user not found."));

        Friendship friendship =
                friendshipRepository
                        .findByRequesterAndAddressee(requester, addressee)
                        .orElseThrow(() -> new NotFoundException("Request not found."));

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new InvalidInputException("Only pending requests can be rejected.");
        }

        friendship.setStatus(FriendshipStatus.REJECTED);
        friendshipRepository.save(friendship);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void blockUser(User blocker, UUID blockedId) {
        if (blocker.getId().equals(blockedId)) {
            throw new InvalidInputException("You can't block yourself");
        }

        User blocked =
                userRepository
                        .findById(blockedId)
                        .orElseThrow(() -> new NotFoundException("User to block not found"));

        Optional<Friendship> existingRelation =
                friendshipRepository.findFriendshipBetween(blocker, blocked);

        if (existingRelation.isPresent()) {
            Friendship friendship = existingRelation.get();

            if (friendship.getStatus() == FriendshipStatus.BLOCKED) {
                throw new ResourceAlreadyInUseException("This user is already blocked.");
            }

            friendship.setRequester(blocker);
            friendship.setAddressee(blocked);
            friendship.setStatus(FriendshipStatus.BLOCKED);
            friendshipRepository.save(friendship);
            return;
        }

        Friendship newFriendship =
                Friendship.builder()
                        .requester(blocker)
                        .addressee(blocked)
                        .status(FriendshipStatus.BLOCKED)
                        .build();

        friendshipRepository.save(newFriendship);
    }

    @Transactional(readOnly = true)
    public List<Friendship> getFriends(User user) {
        return friendshipRepository.findAllFriendshipsByUserAndStatus(
                user, FriendshipStatus.ACCEPTED);
    }

    @Transactional(readOnly = true)
    public List<Friendship> getPendingRequests(User user) {
        return friendshipRepository.findByAddresseeAndStatus(user, FriendshipStatus.PENDING);
    }

    @Transactional
    public void removeFriend(User user, UUID friendId) {
        if (user.getId().equals(friendId)) {
            throw new InvalidInputException("You can't remove yourself as a friend");
        }

        User friend =
                userRepository
                        .findById(friendId)
                        .orElseThrow(() -> new NotFoundException("Friend not found"));

        Friendship friendship =
                friendshipRepository
                        .findFriendshipBetween(user, friend)
                        .orElseThrow(() -> new NotFoundException("Friendship not found"));

        if (friendship.getStatus() != FriendshipStatus.ACCEPTED) {
            throw new InvalidInputException("Only accepted friendships can be removed");
        }

        friendshipRepository.delete(friendship);
    }

    @Transactional
    public void unblockUser(User user, UUID unblockedId) {
        if (user.getId().equals(unblockedId)) {
            throw new InvalidInputException("You can't unblock yourself");
        }

        User unblocked =
                userRepository
                        .findById(unblockedId)
                        .orElseThrow(() -> new NotFoundException("User to unblock not found"));

        Friendship friendship =
                friendshipRepository
                        .findByRequesterAndAddressee(user, unblocked)
                        .orElseThrow(
                                () -> new NotFoundException("This user is not blocked by you"));

        if (friendship.getStatus() != FriendshipStatus.BLOCKED) {
            throw new InvalidInputException("This user is not blocked");
        }

        friendshipRepository.delete(friendship);
    }

    @Transactional
    public void cancelFriendRequest(User requester, UUID addresseeId) {
        User addressee =
                userRepository
                        .findById(addresseeId)
                        .orElseThrow(() -> new NotFoundException("Destination user not found"));

        Friendship friendship =
                friendshipRepository
                        .findByRequesterAndAddressee(requester, addressee)
                        .orElseThrow(() -> new NotFoundException("Request not found."));

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new InvalidInputException("Only pending requests can be cancelled.");
        }

        friendshipRepository.delete(friendship);
    }

    @Transactional(readOnly = true)
    public List<Friendship> getSentRequests(User user) {
        return friendshipRepository.findByRequesterAndStatus(user, FriendshipStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<FriendResponse> getFriendsWithStats(User user) {
        return getFriends(user).stream()
                .map(
                        f -> {
                            User friend =
                                    f.getRequester().getId().equals(user.getId())
                                            ? f.getAddressee()
                                            : f.getRequester();
                            UserStats stats =
                                    userStatsRepository.findByUserId(friend.getId()).orElse(null);
                            return FriendResponse.from(f, user, stats);
                        })
                .toList();
    }

    /** A failed notification must not roll back the friendship change. */
    private void notifyRequestReceived(User addressee, User requester) {
        notifySafely(addressee, NotificationCode.FRIEND_REQUEST_RECEIVED, requester);
    }

    private void notifyRequestAccepted(User requester, User addressee) {
        notifySafely(requester, NotificationCode.FRIEND_REQUEST_ACCEPTED, addressee);
    }

    private void notifySafely(User recipient, NotificationCode code, User about) {
        try {
            notificationService.notifyUser(
                    recipient.getId(),
                    code,
                    Map.of(
                            "friend", about.getName(),
                            "friendUsername", about.getUsername(),
                            "friendId", about.getId().toString()));
        } catch (RuntimeException ex) {
            log.warn("Could not send {} notification to {}", code, recipient.getId(), ex);
        }
    }
}
