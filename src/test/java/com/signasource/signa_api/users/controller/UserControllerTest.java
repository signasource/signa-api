package com.signasource.signa_api.users.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.dto.AuthResponse;
import com.signasource.signa_api.auth.dto.ChangePasswordRequest;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.auth.service.AuthService;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.users.dto.UpdateUsernameRequest;
import com.signasource.signa_api.users.dto.UsernameAvailabilityResponse;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final String EMAIL = "user@example.com";
    private static final String USERNAME = "testuser";
    private static final String NEW_USERNAME = "newuser";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    @Mock private AuthService authService;
    @Mock private UserService userService;

    @InjectMocks private UserController userController;

    private User user;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        user =
                User.builder()
                        .email(EMAIL)
                        .username(USERNAME)
                        .name("Test User")
                        .passwordHash("hashed")
                        .role(Role.USER)
                        .enabled(true)
                        .build();
        userDetails = new CustomUserDetails(user);
    }

    @Test
    void changePassword_returnsOkWithTokens() {
        ChangePasswordRequest request = new ChangePasswordRequest("current", "newPass1!");
        AuthResponse authResponse = new AuthResponse(ACCESS_TOKEN, REFRESH_TOKEN);
        when(authService.changePassword(request)).thenReturn(authResponse);

        ResponseEntity<AuthResponse> response = userController.changePassword(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(authResponse, response.getBody());
    }

    @Test
    void checkUsernameAvailability_whenAvailable_returnsTrue() {
        when(userService.checkUsernameAvailability(NEW_USERNAME))
                .thenReturn(new UsernameAvailabilityResponse(true));

        ResponseEntity<UsernameAvailabilityResponse> response =
                userController.checkUsernameAvailability(NEW_USERNAME);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(true, response.getBody().available());
        verify(userService).checkUsernameAvailability(NEW_USERNAME);
    }

    @Test
    void checkUsernameAvailability_whenTaken_returnsFalse() {
        when(userService.checkUsernameAvailability(NEW_USERNAME))
                .thenReturn(new UsernameAvailabilityResponse(false));

        ResponseEntity<UsernameAvailabilityResponse> response =
                userController.checkUsernameAvailability(NEW_USERNAME);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(false, response.getBody().available());
    }

    @Test
    void updateUsername_returnsNoContent() {
        UpdateUsernameRequest request = new UpdateUsernameRequest(NEW_USERNAME);
        doNothing().when(userService).updateUsername(eq(request), eq(user));

        ResponseEntity<Void> response = userController.updateUsername(request, userDetails);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(userService).updateUsername(request, user);
    }

    @Test
    void updateUsername_whenConflict_propagatesException() {
        UpdateUsernameRequest request = new UpdateUsernameRequest(NEW_USERNAME);
        doThrow(new ResourceAlreadyInUseException("Username already in use"))
                .when(userService)
                .updateUsername(any(), any());

        org.junit.jupiter.api.Assertions.assertThrows(
                ResourceAlreadyInUseException.class,
                () -> userController.updateUsername(request, userDetails));
    }
}
