package com.signasource.signa_api.users.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signasource.signa_api.users.entity.User;
import org.junit.jupiter.api.Test;

class AvatarLinksTest {

    private static User userWith(String avatar) {
        return User.builder().username("camila").avatar(avatar).build();
    }

    @Test
    void of_NullWhenNoPhoto() {
        assertNull(AvatarLinks.of(userWith(null)));
        assertNull(AvatarLinks.of(userWith("")));
    }

    @Test
    void of_PointsToUsersAvatarEndpoint() {
        String link = AvatarLinks.of(userWith("data:image/png;base64,AAAA"));

        assertTrue(link.startsWith("/users/camila/avatar?v="));
    }

    @Test
    void of_VersionChangesWithThePhoto() {
        String first = AvatarLinks.of(userWith("data:image/png;base64,AAAA"));
        String second = AvatarLinks.of(userWith("data:image/png;base64,BBBB"));

        assertNotEquals(first, second);
        assertEquals(first, AvatarLinks.of(userWith("data:image/png;base64,AAAA")));
    }
}
