package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String username,
        String name,
        String lastName,
        Role role,
        boolean enabled,
        boolean verified,
        String organizationName) {

    public static AdminUserResponse from(User user, String organizationName) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getName(),
                user.getLastName(),
                user.getRole(),
                user.isEnabled(),
                user.isVerified(),
                organizationName);
    }
}
