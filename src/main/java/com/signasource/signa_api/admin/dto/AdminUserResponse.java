package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.users.entity.AuthProvider;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import java.time.LocalDate;
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
        String organizationName,
        String authProvider,
        int currentStreak,
        LocalDate lastStreakDate) {

    public static AdminUserResponse from(User user, String organizationName, UserStats stats) {
        String provider =
                (user.getProviders() != null && user.getProviders().contains(AuthProvider.GOOGLE))
                        ? "GOOGLE"
                        : "EMAIL";
        int streak = stats != null ? stats.getCurrentStreak() : 0;
        LocalDate lastActivity = stats != null ? stats.getLastStreakDate() : null;
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getUsername(),
                user.getName(),
                user.getLastName(),
                user.getRole(),
                user.isEnabled(),
                user.isVerified(),
                organizationName,
                provider,
                streak,
                lastActivity);
    }
}
