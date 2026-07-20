// users/dto/PublicUserProfileResponse.java
package com.signasource.signa_api.users.dto;

import com.signasource.signa_api.users.entity.User;
import java.util.UUID;

public record PublicUserProfileResponse(UUID id, String username, String name) {
    public static PublicUserProfileResponse from(User user) {
        return new PublicUserProfileResponse(user.getId(), user.getUsername(), user.getName());
    }
}