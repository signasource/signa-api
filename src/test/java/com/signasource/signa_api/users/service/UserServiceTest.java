package com.signasource.signa_api.users.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.users.dto.PublicUserProfileResponse;
import com.signasource.signa_api.users.dto.UpdateUsernameRequest;
import com.signasource.signa_api.users.dto.UsernameAvailabilityResponse;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
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

    @InjectMocks private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user =
                User.builder()
                        .email("user@example.com")
                        .username(CURRENT_USERNAME)
                        .name("Test User")
                        .passwordHash("hashed")
                        .role(Role.USER)
                        .enabled(true)
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
    void getPublicProfileByUsername_whenUserExists_returnsPublicProfile() {
        when(userRepository.findByUsername(CURRENT_USERNAME)).thenReturn(Optional.of(user));

        PublicUserProfileResponse response =
                userService.getPublicProfileByUsername(CURRENT_USERNAME);

        assertEquals(user.getId(), response.id());
        assertEquals(CURRENT_USERNAME, response.username());
        assertEquals(user.getName(), response.name());
    }

    @Test
    void getPublicProfileByUsername_whenUserDoesNotExist_throwsNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class, () -> userService.getPublicProfileByUsername("ghost"));
    }

    @Test
    void deleteAccount_disablesUserAndObfuscatesEmail() {
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
    }
}
