package com.signasource.signa_api.users.service;

import com.signasource.signa_api.auth.repository.TokenRepository;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.notification.repository.DeviceTokenRepository;
import com.signasource.signa_api.users.dto.RelationStatus;
import com.signasource.signa_api.users.dto.UpdateUsernameRequest;
import com.signasource.signa_api.users.dto.UserSearchResultResponse;
import com.signasource.signa_api.users.dto.UsernameAvailabilityResponse;
import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.FriendshipRepository;
import com.signasource.signa_api.users.repository.UserRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final FriendshipRepository friendshipRepository;

    public UsernameAvailabilityResponse checkUsernameAvailability(String username) {
        return new UsernameAvailabilityResponse(!userRepository.existsByUsername(username));
    }

    @Transactional
    public void updateUsername(UpdateUsernameRequest request, User user) {
        if (user.getUsername().equals(request.username())) {
            return;
        }

        if (userRepository.existsByUsername(request.username())) {
            throw new ResourceAlreadyInUseException("Username already in use");
        }

        user.setUsername(request.username());
        userRepository.save(user);
    }

    @Transactional
    public void deleteAccount(User user) {
        user.setEnabled(false);
        user.setEmail("deleted_" + user.getId() + "@signa.invalid");
        userRepository.save(user);

        tokenRepository.deleteByUser(user);
        deviceTokenRepository.deleteByUser(user);
    }

    /** Resolves each match against the caller. Users who blocked the caller are left out. */
    @Transactional(readOnly = true)
    public List<UserSearchResultResponse> searchUsers(User requester, String query, int limit) {
        List<User> matches =
                userRepository.searchByUsernameOrName(
                        query.trim(), requester.getId(), PageRequest.of(0, limit));

        if (matches.isEmpty()) {
            return List.of();
        }

        Map<UUID, RelationStatus> relations = relationsOf(requester);
        Set<UUID> myFriendIds =
                relations.entrySet().stream()
                        .filter(e -> e.getValue() == RelationStatus.FRIEND)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet());

        return matches.stream()
                .map(
                        u ->
                                new UserSearchResultResponse(
                                        u.getId(),
                                        u.getUsername(),
                                        u.getName(),
                                        relations.getOrDefault(u.getId(), RelationStatus.NONE),
                                        countMutualFriends(u.getId(), myFriendIds)))
                .filter(r -> r.relation() != RelationStatus.BLOCKED_BY)
                .toList();
    }

    private long countMutualFriends(UUID candidateId, Set<UUID> myFriendIds) {
        if (myFriendIds.isEmpty()) {
            return 0L;
        }
        return friendshipRepository.countMutualFriends(candidateId, myFriendIds);
    }

    /** Every relation of the requester, keyed by the other user's id. */
    private Map<UUID, RelationStatus> relationsOf(User requester) {
        Map<UUID, RelationStatus> relations = new HashMap<>();

        for (Friendship f : friendshipRepository.findAllByUser(requester)) {
            boolean iRequested = f.getRequester().getId().equals(requester.getId());
            UUID otherId = iRequested ? f.getAddressee().getId() : f.getRequester().getId();

            RelationStatus status =
                    switch (f.getStatus()) {
                        case ACCEPTED -> RelationStatus.FRIEND;
                        case PENDING ->
                                iRequested ? RelationStatus.OUTGOING : RelationStatus.INCOMING;
                        case BLOCKED ->
                                iRequested ? RelationStatus.BLOCKED : RelationStatus.BLOCKED_BY;
                        case REJECTED -> RelationStatus.NONE;
                    };

            relations.put(otherId, status);
        }

        return relations;
    }
}
