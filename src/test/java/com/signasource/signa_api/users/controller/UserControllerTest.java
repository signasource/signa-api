package com.signasource.signa_api.users.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.signasource.signa_api.gamification.dto.DailyXpResponse;
import com.signasource.signa_api.gamification.dto.UserStatsResponse;
import com.signasource.signa_api.gamification.entity.LivesMode;
import com.signasource.signa_api.gamification.service.UserStatsService;
import com.signasource.signa_api.users.dto.DailyGoalResponse;
import com.signasource.signa_api.users.dto.PublicUserProfileResponse;
import com.signasource.signa_api.users.dto.PublicUserStatsResponse;
import com.signasource.signa_api.users.dto.RelationStatus;
import com.signasource.signa_api.users.dto.UpdateDailyGoalRequest;
import com.signasource.signa_api.users.dto.UpdateUserSettingsRequest;
import com.signasource.signa_api.users.dto.UpdateUsernameRequest;
import com.signasource.signa_api.users.dto.UserProfileResponse;
import com.signasource.signa_api.users.dto.UserSearchResultResponse;
import com.signasource.signa_api.users.dto.UserSettingsResponse;
import com.signasource.signa_api.users.dto.UsernameAvailabilityResponse;
import com.signasource.signa_api.users.entity.AccountVisibility;
import com.signasource.signa_api.users.entity.FontSize;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.Theme;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.service.PublicProfileService;
import com.signasource.signa_api.users.service.UserService;
import com.signasource.signa_api.users.service.UserSettingsService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
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
    @Mock private PublicProfileService publicProfileService;
    @Mock private UserService userService;
    @Mock private UserStatsService userStatsService;

    @InjectMocks private UserController userController;

    private User user;
    private CustomUserDetails userDetails;

    @BeforeEach
    void setUp() {
        user =
                User.builder()
                        .id(UUID.randomUUID())
                        .email(EMAIL)
                        .username(USERNAME)
                        .name("Test User")
                        .passwordHash("hashed_password")
                        .role(Role.USER)
                        .enabled(true)
                        .verified(true)
                        .build();
        userDetails = new CustomUserDetails(user);
    }

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
        when(userSettingsService.getSettings(user)).thenReturn(expected);

        ResponseEntity<UserSettingsResponse> response = userController.getSettings(userDetails);

        verify(userSettingsService).getSettings(user);
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
                        DAILY_NOTIFICATION_TIME,
                        null);
        UserSettingsResponse expected = buildSettingsResponse();
        when(userSettingsService.updateSettings(user, request)).thenReturn(expected);

        ResponseEntity<UserSettingsResponse> response =
                userController.updateSettings(userDetails, request);

        verify(userSettingsService).updateSettings(user, request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }

    @Test
    void shouldSetDailyGoalAndReturn201() {
        UpdateDailyGoalRequest request = new UpdateDailyGoalRequest(DAILY_GOAL_MINUTES);
        DailyGoalResponse expected = new DailyGoalResponse(DAILY_GOAL_MINUTES);
        when(userSettingsService.setDailyGoal(user, request)).thenReturn(expected);

        ResponseEntity<DailyGoalResponse> response =
                userController.setDailyGoal(userDetails, request);

        verify(userSettingsService).setDailyGoal(user, request);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }

    @Test
    void shouldReturnDailyGoal() {
        DailyGoalResponse expected = new DailyGoalResponse(DAILY_GOAL_MINUTES);
        when(userSettingsService.getDailyGoal(user)).thenReturn(expected);

        ResponseEntity<DailyGoalResponse> response = userController.getDailyGoal(userDetails);

        verify(userSettingsService).getDailyGoal(user);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }

    @Test
    void shouldReturnUpdatedDailyGoal() {
        UpdateDailyGoalRequest request = new UpdateDailyGoalRequest(DAILY_GOAL_MINUTES);
        DailyGoalResponse expected = new DailyGoalResponse(DAILY_GOAL_MINUTES);
        when(userSettingsService.updateDailyGoal(user, request)).thenReturn(expected);

        ResponseEntity<DailyGoalResponse> response =
                userController.updateDailyGoal(userDetails, request);

        verify(userSettingsService).updateDailyGoal(user, request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
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

    private UserSettingsResponse buildSettingsResponse() {
        return new UserSettingsResponse(
                TIMEZONE,
                false,
                THEME,
                FONT_SIZE,
                false,
                ACCOUNT_VISIBILITY,
                DAILY_GOAL_MINUTES,
                true,
                DAILY_NOTIFICATION_TIME,
                "#FFFFFF");
    }

    @Test
    void shouldReturnCurrentUserProfile() {
        ResponseEntity<UserProfileResponse> response = userController.getMe(userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        UserProfileResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(user.getId(), body.id());
        assertEquals(EMAIL, body.email());
        assertEquals(USERNAME, body.username());
        assertEquals(user.getName(), body.name());
        assertEquals(Role.USER, body.role());
        assertEquals(true, body.enabled());
        assertEquals(true, body.verified());
    }

    @Test
    void shouldReturnPublicProfileByUsername_whenAuthenticated() {
        PublicUserProfileResponse expected = publicProfile();
        when(publicProfileService.getByUsername(USERNAME, user)).thenReturn(expected);

        ResponseEntity<PublicUserProfileResponse> response =
                userController.getByUsername(USERNAME, userDetails);

        verify(publicProfileService).getByUsername(USERNAME, user);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }

    @Test
    void shouldReturnPublicProfileByUsername_whenAnonymous() {
        PublicUserProfileResponse expected = publicProfile();
        when(publicProfileService.getByUsername(USERNAME, null)).thenReturn(expected);

        ResponseEntity<PublicUserProfileResponse> response =
                userController.getByUsername(USERNAME, null);

        verify(publicProfileService).getByUsername(USERNAME, null);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }

    @Test
    void shouldDeleteAccount_returnsNoContent() {
        doNothing().when(userService).deleteAccount(user);

        ResponseEntity<Void> response = userController.deleteMe(userDetails);

        verify(userService).deleteAccount(user);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
    }

    @Test
    void shouldReturnWeeklyXpBreakdown() {
        List<DailyXpResponse> expected =
                List.of(
                        new DailyXpResponse(LocalDate.of(2026, 8, 10), 100),
                        new DailyXpResponse(LocalDate.of(2026, 8, 11), 0),
                        new DailyXpResponse(LocalDate.of(2026, 8, 12), 50));
        when(userStatsService.getWeeklyXpBreakdown(user)).thenReturn(expected);

        ResponseEntity<List<DailyXpResponse>> response =
                userController.getWeeklyXpBreakdown(userDetails);

        verify(userStatsService).getWeeklyXpBreakdown(user);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }

    private PublicUserProfileResponse publicProfile() {
        return new PublicUserProfileResponse(
                user.getId(),
                USERNAME,
                user.getName(),
                "#FFFFFF",
                RelationStatus.NONE,
                true,
                PublicUserStatsResponse.EMPTY,
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    void searchUsers_ReturnsOkWithResults() {
        UserSearchResultResponse result =
                new UserSearchResultResponse(
                        UUID.randomUUID(), "camisosa", "Camila Sosa", RelationStatus.NONE, 3L);
        when(userService.searchUsers(user, "cami", 20)).thenReturn(List.of(result));

        ResponseEntity<List<UserSearchResultResponse>> response =
                userController.searchUsers("cami", 20, userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals(3L, response.getBody().get(0).mutualFriends());
    }

    @Test
    void searchUsers_CapsAndFloorsTheLimit() {
        when(userService.searchUsers(user, "cami", 50)).thenReturn(List.of());
        when(userService.searchUsers(user, "cami", 1)).thenReturn(List.of());

        userController.searchUsers("cami", 500, userDetails);
        userController.searchUsers("cami", 0, userDetails);

        verify(userService).searchUsers(user, "cami", 50);
        verify(userService).searchUsers(user, "cami", 1);
    }

    @Test
    void shouldReturnMeStats() {
        UserStatsResponse expected =
                new UserStatsResponse(
                        1500,
                        200,
                        7,
                        14,
                        50,
                        1,
                        30,
                        3,
                        LivesMode.LIMITED,
                        null,
                        false,
                        1.0,
                        null,
                        null);
        when(userStatsService.getStats(user)).thenReturn(expected);

        ResponseEntity<UserStatsResponse> response = userController.getMeStats(userDetails);

        verify(userStatsService).getStats(user);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(expected, response.getBody());
    }
}
