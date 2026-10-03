package com.signasource.signa_api.users.dto;

import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String username,
        String name,
        String lastName,
        Role role,
        boolean enabled,
        boolean verified) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getName(),
                user.getLastName(),
                user.getRole(),
                user.isEnabled(),
                user.isVerified());
    }
}
