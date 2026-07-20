package com.signasource.signa_api.users.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.users.dto.PublicUserProfileResponse;
import com.signasource.signa_api.users.dto.UpdateUsernameRequest;
import com.signasource.signa_api.users.dto.UsernameAvailabilityResponse;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UsernameAvailabilityResponse checkUsernameAvailability(String username) {
        return new UsernameAvailabilityResponse(!userRepository.existsByUsername(username));
    }

    public PublicUserProfileResponse getPublicProfileByUsername(String username) {
        User user =
                userRepository
                        .findByUsername(username)
                        .orElseThrow(() -> new NotFoundException("User not found"));
        return PublicUserProfileResponse.from(user);
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

    public void deleteAccount(User user) {
        user.setEnabled(false);
        user.setEmail("deleted_" + user.getId() + "@signa.invalid");
        userRepository.save(user);
        //  revocar sus tokens activos
    }
}
