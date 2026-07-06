package com.signasource.signa_api.users.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.dto.AuthResponse;
import com.signasource.signa_api.auth.dto.ChangePasswordRequest;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.auth.service.AuthService;
import com.signasource.signa_api.users.dto.UpdateUserSettingsRequest;
import com.signasource.signa_api.users.dto.UserSettingsResponse;
import com.signasource.signa_api.users.entity.AccountVisibility;
import com.signasource.signa_api.users.entity.FontSize;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.Theme;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.service.UserSettingsService;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    private static final UUID SETTINGS_ID = UUID.randomUUID();
    private static final String TIMEZONE = "America/Argentina/Buenos_Aires";
    private static final Theme THEME = Theme.DARK;
    private static final FontSize FONT_SIZE = FontSize.LARGE;
    private static final AccountVisibility ACCOUNT_VISIBILITY = AccountVisibility.PUBLIC;
    private static final int DAILY_GOAL_MINUTES = 45;
    private static final LocalTime DAILY_NOTIFICATION_TIME = LocalTime.of(8, 30);
    private static final String CURRENT_PASSWORD = "current-password";
    private static final String NEW_PASSWORD = "new-password";
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";

    @Mock private AuthService authService;
    @Mock private UserSettingsService userSettingsService;

    @InjectMocks private UserController userController;

    private final CustomUserDetails userDetails =
            new CustomUserDetails(
                    User.builder()
                            .id(UUID.randomUUID())
                            .email("test@example.com")
                            .name("Test User")
                            .passwordHash("hashed_password")
                            .role(Role.USER)
                            .enabled(true)
                            .build());

    @Test
    void shouldChangePassword() {
        ChangePasswordRequest request = new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD);
        AuthResponse expected = new AuthResponse(ACCESS_TOKEN, REFRESH_TOKEN);
        when(authService.changePassword(request)).thenReturn(expected);

        ResponseEntity<AuthResponse> response = userController.changePassword(request);

        verify(authService).changePassword(request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }

    @Test
    void shouldReturnUserSettings() {
        UserSettingsResponse expected = buildSettingsResponse();
        when(userSettingsService.getSettings(userDetails.getUser())).thenReturn(expected);

        ResponseEntity<UserSettingsResponse> response = userController.getSettings(userDetails);

        verify(userSettingsService).getSettings(userDetails.getUser());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }

    @Test
    void shouldReturnUpdatedSettings() {
        UpdateUserSettingsRequest request =
                new UpdateUserSettingsRequest(
                        TIMEZONE,
                        false,
                        THEME,
                        FONT_SIZE,
                        false,
                        ACCOUNT_VISIBILITY,
                        DAILY_GOAL_MINUTES,
                        true,
                        DAILY_NOTIFICATION_TIME);
        UserSettingsResponse expected = buildSettingsResponse();
        when(userSettingsService.updateSettings(userDetails.getUser(), request))
                .thenReturn(expected);

        ResponseEntity<UserSettingsResponse> response =
                userController.updateSettings(userDetails, request);

        verify(userSettingsService).updateSettings(userDetails.getUser(), request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }

    private UserSettingsResponse buildSettingsResponse() {
        return new UserSettingsResponse(
                SETTINGS_ID,
                TIMEZONE,
                false,
                THEME,
                FONT_SIZE,
                false,
                ACCOUNT_VISIBILITY,
                DAILY_GOAL_MINUTES,
                true,
                DAILY_NOTIFICATION_TIME);
    }
}
