package com.signasource.signa_api.users.service;

import com.signasource.signa_api.auth.repository.TokenRepository;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.notification.repository.DeviceTokenRepository;
import com.signasource.signa_api.users.dto.PublicUserProfileResponse;
import com.signasource.signa_api.users.dto.UpdateUsernameRequest;
import com.signasource.signa_api.users.dto.UsernameAvailabilityResponse;
import com.signasource.signa_api.users.entity.AccountVisibility;
import com.signasource.signa_api.users.entity.FriendshipStatus;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.FriendshipRepository;
import com.signasource.signa_api.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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

    public PublicUserProfileResponse getPublicProfileByUsername(String username, User requester) {
        User target =
                userRepository
                        .findByUsername(username)
                        .orElseThrow(() -> new NotFoundException("User not found"));

        if (!canView(target, requester)) {

            throw new NotFoundException("User not found");
        }

        return PublicUserProfileResponse.from(target);
    }

    private boolean canView(User target, User requester) {
        if (!target.isEnabled()) {
            return false;
        }
        if (target.getAccountVisibility() == AccountVisibility.PUBLIC) {
            return true;
        }
        if (requester == null) {
            return false;
        }
        if (target.getId().equals(requester.getId())) {
            return true;
        }
        return friendshipRepository
                .findFriendshipBetween(requester, target)
                .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                .isPresent();
    }

    @Transactional
    public void deleteAccount(User user) {
        user.setEnabled(false);
        user.setEmail("deleted_" + user.getId() + "@signa.invalid");
        userRepository.save(user);

        tokenRepository.deleteByUser(user);
        deviceTokenRepository.deleteByUser(user);
    }
}
