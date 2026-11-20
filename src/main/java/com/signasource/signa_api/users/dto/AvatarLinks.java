package com.signasource.signa_api.users.dto;

import com.signasource.signa_api.users.entity.User;

/**
 * Where a user's profile photo can be fetched. Responses carry this path instead of the photo
 * itself so lists (ranking, friends, search) stay small; the {@code v} query value changes whenever
 * the photo does, which lets clients cache the image indefinitely.
 */
public final class AvatarLinks {

    private AvatarLinks() {}

    /** Relative path to the photo, or null when the user has none. */
    public static String of(User user) {
        String avatar = user.getAvatar();
        if (avatar == null || avatar.isEmpty()) return null;
        return "/users/" + user.getUsername() + "/avatar?v=" + version(avatar);
    }

    static String version(String avatar) {
        return Integer.toHexString(avatar.hashCode());
    }
}
