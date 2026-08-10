package com.signasource.signa_api.users.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.repository.TokenRepository;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.notification.repository.DeviceTokenRepository;
import com.signasource.signa_api.users.dto.PublicUserProfileResponse;
import com.signasource.signa_api.users.dto.UpdateUsernameRequest;
import com.signasource.signa_api.users.dto.UsernameAvailabilityResponse;
import com.signasource.signa_api.users.entity.AccountVisibility;
import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.FriendshipStatus;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.FriendshipRepository;
import com.signasource.signa_api.users.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String CURRENT_USERNAME = "currentuser";
    private static final String NEW_USERNAME = "newuser";
    private static final String TAKEN_USERNAME = "takenuser";

    @Mock private UserRepository userRepository;
    @Mock private TokenRepository tokenRepository;
    @Mock private DeviceTokenRepository deviceTokenRepository;
    @Mock private FriendshipRepository friendshipRepository;

    @InjectMocks private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user =
                User.builder()
                        .id(UUID.randomUUID())
                        .email("user@example.com")
                        .username(CURRENT_USERNAME)
                        .name("Test User")
                        .passwordHash("hashed")
                        .role(Role.USER)
                        .enabled(true)
                        .accountVisibility(AccountVisibility.PUBLIC)
                        .build();
    }

    @Test
    void checkUsernameAvailability_whenUsernameIsFree_returnsAvailableTrue() {
        when(userRepository.existsByUsername(NEW_USERNAME)).thenReturn(false);

        UsernameAvailabilityResponse response = userService.checkUsernameAvailability(NEW_USERNAME);

        assertTrue(response.available());
    }

    @Test
    void checkUsernameAvailability_whenUsernameTaken_returnsAvailableFalse() {
        when(userRepository.existsByUsername(TAKEN_USERNAME)).thenReturn(true);

        UsernameAvailabilityResponse response =
                userService.checkUsernameAvailability(TAKEN_USERNAME);

        assertFalse(response.available());
    }

    @Test
    void updateUsername_whenNewUsernameIsFree_updatesAndSaves() {
        when(userRepository.existsByUsername(NEW_USERNAME)).thenReturn(false);

        userService.updateUsername(new UpdateUsernameRequest(NEW_USERNAME), user);

        assertEquals(NEW_USERNAME, user.getUsername());
        verify(userRepository).save(user);
    }

    @Test
    void updateUsername_whenSameAsCurrentUsername_doesNothing() {
        userService.updateUsername(new UpdateUsernameRequest(CURRENT_USERNAME), user);

        verify(userRepository, never()).existsByUsername(CURRENT_USERNAME);
        verify(userRepository, never()).save(user);
    }

    @Test
    void updateUsername_whenUsernameTaken_throwsConflict() {
        when(userRepository.existsByUsername(TAKEN_USERNAME)).thenReturn(true);

        assertThrows(
                ResourceAlreadyInUseException.class,
                () -> userService.updateUsername(new UpdateUsernameRequest(TAKEN_USERNAME), user));

        verify(userRepository, never()).save(user);
    }

    @Test
    void getPublicProfileByUsername_whenUserDoesNotExist_throwsNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> userService.getPublicProfileByUsername("ghost", null));
    }

    @Test
    void getPublicProfileByUsername_whenAccountIsPublic_returnsProfileEvenWithoutRequester() {
        when(userRepository.findByUsername(CURRENT_USERNAME)).thenReturn(Optional.of(user));

        PublicUserProfileResponse response =
                userService.getPublicProfileByUsername(CURRENT_USERNAME, null);

        assertEquals(user.getId(), response.id());
        assertEquals(CURRENT_USERNAME, response.username());
        assertEquals(user.getName(), response.name());
    }

    @Test
    void getPublicProfileByUsername_whenRequesterIsSameUser_returnsProfile() {
        user.setAccountVisibility(AccountVisibility.PRIVATE);
        when(userRepository.findByUsername(CURRENT_USERNAME)).thenReturn(Optional.of(user));

        PublicUserProfileResponse response =
                userService.getPublicProfileByUsername(CURRENT_USERNAME, user);

        assertEquals(user.getId(), response.id());
    }

    @Test
    void getPublicProfileByUsername_whenPrivateAndNoRequester_throwsNotFound() {
        user.setAccountVisibility(AccountVisibility.PRIVATE);
        when(userRepository.findByUsername(CURRENT_USERNAME)).thenReturn(Optional.of(user));

        assertThrows(
                NotFoundException.class,
                () -> userService.getPublicProfileByUsername(CURRENT_USERNAME, null));
    }

    @Test
    void getPublicProfileByUsername_whenPrivateAndRequesterNotFriend_throwsNotFound() {
        user.setAccountVisibility(AccountVisibility.PRIVATE);
        User requester =
                User.builder()
                        .id(UUID.randomUUID())
                        .email("other@example.com")
                        .username("otheruser")
                        .name("Other User")
                        .passwordHash("hashed")
                        .role(Role.USER)
                        .enabled(true)
                        .build();
        when(userRepository.findByUsername(CURRENT_USERNAME)).thenReturn(Optional.of(user));
        when(friendshipRepository.findFriendshipBetween(requester, user))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> userService.getPublicProfileByUsername(CURRENT_USERNAME, requester));
    }

    @Test
    void getPublicProfileByUsername_whenPrivateAndRequesterIsAcceptedFriend_returnsProfile() {
        user.setAccountVisibility(AccountVisibility.PRIVATE);
        User requester =
                User.builder()
                        .id(UUID.randomUUID())
                        .email("other@example.com")
                        .username("otheruser")
                        .name("Other User")
                        .passwordHash("hashed")
                        .role(Role.USER)
                        .enabled(true)
                        .build();
        Friendship friendship =
                Friendship.builder()
                        .requester(requester)
                        .addressee(user)
                        .status(FriendshipStatus.ACCEPTED)
                        .build();
        when(userRepository.findByUsername(CURRENT_USERNAME)).thenReturn(Optional.of(user));
        when(friendshipRepository.findFriendshipBetween(requester, user))
                .thenReturn(Optional.of(friendship));

        PublicUserProfileResponse response =
                userService.getPublicProfileByUsername(CURRENT_USERNAME, requester);

        assertEquals(user.getId(), response.id());
    }

    @Test
    void getPublicProfileByUsername_whenPrivateAndRequesterIsPendingFriend_throwsNotFound() {
        user.setAccountVisibility(AccountVisibility.PRIVATE);
        User requester =
                User.builder()
                        .id(UUID.randomUUID())
                        .email("other@example.com")
                        .username("otheruser")
                        .name("Other User")
                        .passwordHash("hashed")
                        .role(Role.USER)
                        .enabled(true)
                        .build();
        Friendship friendship =
                Friendship.builder()
                        .requester(requester)
                        .addressee(user)
                        .status(FriendshipStatus.PENDING)
                        .build();
        when(userRepository.findByUsername(CURRENT_USERNAME)).thenReturn(Optional.of(user));
        when(friendshipRepository.findFriendshipBetween(requester, user))
                .thenReturn(Optional.of(friendship));

        assertThrows(
                NotFoundException.class,
                () -> userService.getPublicProfileByUsername(CURRENT_USERNAME, requester));
    }

    @Test
    void deleteAccount_disablesUserObfuscatesEmailAndRevokesTokens() {
        UUID userId = UUID.randomUUID();
        user =
                User.builder()
                        .id(userId)
                        .email("user@example.com")
                        .username(CURRENT_USERNAME)
                        .name("Test User")
                        .passwordHash("hashed")
                        .role(Role.USER)
                        .enabled(true)
                        .build();

        userService.deleteAccount(user);

        assertFalse(user.isEnabled());
        assertTrue(user.getEmail().startsWith("deleted_" + userId));
        verify(userRepository).save(user);
        verify(tokenRepository).deleteByUser(user);
        verify(deviceTokenRepository).deleteByUser(user);
    }
}
