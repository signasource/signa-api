package com.signasource.signa_api.users.service;

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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

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
                return;
            } else {
                throw new ResourceAlreadyInUseException(
                        "The relationship or request already exists.");
            }
        }

        Friendship newFriendship =
                Friendship.builder()
                        .requester(requester)
                        .addressee(addressee)
                        .status(FriendshipStatus.PENDING)
                        .build();

        friendshipRepository.save(newFriendship);
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

    @Transactional
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

            if (friendship.getStatus() == FriendshipStatus.BLOCKED
                    && friendship.getRequester().equals(blocker)) {
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
}
