package com.signasource.signa_api.users.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.users.dto.UpdateUsernameRequest;
import com.signasource.signa_api.users.dto.UsernameAvailabilityResponse;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
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
}
