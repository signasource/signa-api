// users/dto/UserProfileResponse.java
package com.signasource.signa_api.users.dto;

import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import java.util.UUID;

public record UserProfileResponse(
        UUID id, String email, String username, String name, Role role, boolean enabled) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getName(),
                user.getRole(),
                user.isEnabled());
    }
}
