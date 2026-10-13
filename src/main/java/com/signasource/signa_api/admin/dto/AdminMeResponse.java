package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.users.entity.User;
import java.util.UUID;

public record AdminMeResponse(UUID id, String name, String lastName, String email) {
    public static AdminMeResponse from(User user) {
        return new AdminMeResponse(
                user.getId(), user.getName(), user.getLastName(), user.getEmail());
    }
}
