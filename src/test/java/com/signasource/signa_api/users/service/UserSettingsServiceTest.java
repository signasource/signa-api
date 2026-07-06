package com.signasource.signa_api.users.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.users.dto.UpdateUserSettingsRequest;
import com.signasource.signa_api.users.dto.UserSettingsResponse;
import com.signasource.signa_api.users.entity.AccountVisibility;
import com.signasource.signa_api.users.entity.FontSize;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.Theme;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserSettings;
import com.signasource.signa_api.users.repository.UserSettingsRepository;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserSettingsServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SETTINGS_ID = UUID.randomUUID();

    private static final String INITIAL_TIMEZONE = "America/Argentina/Buenos_Aires";
    private static final boolean INITIAL_NOTIFICATIONS_ENABLED = true;
    private static final Theme INITIAL_THEME = Theme.SYSTEM;
    private static final FontSize INITIAL_FONT_SIZE = FontSize.MEDIUM;
    private static final boolean INITIAL_VIBRATION_ENABLED = true;
    private static final AccountVisibility INITIAL_ACCOUNT_VISIBILITY = AccountVisibility.PRIVATE;
    private static final int INITIAL_DAILY_GOAL_MINUTES = 15;
    private static final boolean INITIAL_DAILY_NOTIFICATION_ENABLED = false;
    private static final LocalTime INITIAL_DAILY_NOTIFICATION_TIME = LocalTime.of(20, 0);

    private static final String UPDATED_TIMEZONE = "America/Sao_Paulo";
    private static final boolean UPDATED_NOTIFICATIONS_ENABLED = false;
    private static final Theme UPDATED_THEME = Theme.DARK;
    private static final FontSize UPDATED_FONT_SIZE = FontSize.LARGE;
    private static final boolean UPDATED_VIBRATION_ENABLED = false;
    private static final AccountVisibility UPDATED_ACCOUNT_VISIBILITY = AccountVisibility.PUBLIC;
    private static final int UPDATED_DAILY_GOAL_MINUTES = 45;
    private static final boolean UPDATED_DAILY_NOTIFICATION_ENABLED = true;
    private static final LocalTime UPDATED_DAILY_NOTIFICATION_TIME = LocalTime.of(8, 30);

    @Mock private UserSettingsRepository userSettingsRepository;

    @InjectMocks private UserSettingsService userSettingsService;

    private User user;
    private UserSettings settings;

    @BeforeEach
    void setUp() {
        user =
                User.builder()
                        .id(USER_ID)
                        .email("test@example.com")
                        .name("Test User")
                        .passwordHash("hashed_password")
                        .role(Role.USER)
                        .enabled(true)
                        .build();

        settings =
                UserSettings.builder()
                        .id(SETTINGS_ID)
                        .user(user)
                        .timezone(INITIAL_TIMEZONE)
                        .notificationsEnabled(INITIAL_NOTIFICATIONS_ENABLED)
                        .theme(INITIAL_THEME)
                        .fontSize(INITIAL_FONT_SIZE)
                        .vibrationEnabled(INITIAL_VIBRATION_ENABLED)
                        .accountVisibility(INITIAL_ACCOUNT_VISIBILITY)
                        .dailyGoalMinutes(INITIAL_DAILY_GOAL_MINUTES)
                        .dailyNotificationEnabled(INITIAL_DAILY_NOTIFICATION_ENABLED)
                        .dailyNotificationTime(INITIAL_DAILY_NOTIFICATION_TIME)
                        .build();
    }

    @Test
    void shouldReturnSettingsForUser() {
        when(userSettingsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(settings));

        UserSettingsResponse response = userSettingsService.getSettings(user);

        assertEquals(SETTINGS_ID, response.id());
        assertEquals(INITIAL_TIMEZONE, response.timezone());
        assertEquals(INITIAL_THEME, response.theme());
        verify(userSettingsRepository, never()).save(settings);
    }

    @Test
    void shouldThrowNotFoundWhenGettingSettingsThatDoNotExist() {
        when(userSettingsRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        NotFoundException exception =
                assertThrows(NotFoundException.class, () -> userSettingsService.getSettings(user));

        assertTrue(exception.getMessage().contains(USER_ID.toString()));
    }

    @Test
    void shouldUpdateAllFieldsWhenAllArePresent() {
        UpdateUserSettingsRequest request =
                new UpdateUserSettingsRequest(
                        UPDATED_TIMEZONE,
                        UPDATED_NOTIFICATIONS_ENABLED,
                        UPDATED_THEME,
                        UPDATED_FONT_SIZE,
                        UPDATED_VIBRATION_ENABLED,
                        UPDATED_ACCOUNT_VISIBILITY,
                        UPDATED_DAILY_GOAL_MINUTES,
                        UPDATED_DAILY_NOTIFICATION_ENABLED,
                        UPDATED_DAILY_NOTIFICATION_TIME);
        when(userSettingsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(settings));

        UserSettingsResponse response = userSettingsService.updateSettings(user, request);

        assertEquals(UPDATED_TIMEZONE, response.timezone());
        assertEquals(UPDATED_NOTIFICATIONS_ENABLED, response.notificationsEnabled());
        assertEquals(UPDATED_THEME, response.theme());
        assertEquals(UPDATED_FONT_SIZE, response.fontSize());
        assertEquals(UPDATED_VIBRATION_ENABLED, response.vibrationEnabled());
        assertEquals(UPDATED_ACCOUNT_VISIBILITY, response.accountVisibility());
        assertEquals(UPDATED_DAILY_GOAL_MINUTES, response.dailyGoalMinutes());
        assertEquals(UPDATED_DAILY_NOTIFICATION_ENABLED, response.dailyNotificationEnabled());
        assertEquals(UPDATED_DAILY_NOTIFICATION_TIME, response.dailyNotificationTime());
        verify(userSettingsRepository).save(settings);
    }

    @Test
    void shouldUpdateOnlyProvidedFieldsAndKeepTheRest() {
        UpdateUserSettingsRequest request =
                new UpdateUserSettingsRequest(
                        null,
                        null,
                        UPDATED_THEME,
                        null,
                        null,
                        null,
                        UPDATED_DAILY_GOAL_MINUTES,
                        null,
                        null);
        when(userSettingsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(settings));

        UserSettingsResponse response = userSettingsService.updateSettings(user, request);

        assertEquals(UPDATED_THEME, response.theme());
        assertEquals(UPDATED_DAILY_GOAL_MINUTES, response.dailyGoalMinutes());
        assertEquals(INITIAL_TIMEZONE, response.timezone());
        assertEquals(INITIAL_NOTIFICATIONS_ENABLED, response.notificationsEnabled());
        assertEquals(INITIAL_FONT_SIZE, response.fontSize());
        assertEquals(INITIAL_VIBRATION_ENABLED, response.vibrationEnabled());
        assertEquals(INITIAL_ACCOUNT_VISIBILITY, response.accountVisibility());
        assertEquals(INITIAL_DAILY_NOTIFICATION_ENABLED, response.dailyNotificationEnabled());
        assertEquals(INITIAL_DAILY_NOTIFICATION_TIME, response.dailyNotificationTime());
        verify(userSettingsRepository).save(settings);
    }

    @Test
    void shouldKeepAllFieldsWhenNoFieldIsProvided() {
        UpdateUserSettingsRequest request =
                new UpdateUserSettingsRequest(null, null, null, null, null, null, null, null, null);
        when(userSettingsRepository.findByUserId(USER_ID)).thenReturn(Optional.of(settings));

        UserSettingsResponse response = userSettingsService.updateSettings(user, request);

        assertEquals(INITIAL_TIMEZONE, response.timezone());
        assertEquals(INITIAL_NOTIFICATIONS_ENABLED, response.notificationsEnabled());
        assertEquals(INITIAL_THEME, response.theme());
        assertEquals(INITIAL_FONT_SIZE, response.fontSize());
        assertEquals(INITIAL_VIBRATION_ENABLED, response.vibrationEnabled());
        assertEquals(INITIAL_ACCOUNT_VISIBILITY, response.accountVisibility());
        assertEquals(INITIAL_DAILY_GOAL_MINUTES, response.dailyGoalMinutes());
        assertEquals(INITIAL_DAILY_NOTIFICATION_ENABLED, response.dailyNotificationEnabled());
        assertEquals(INITIAL_DAILY_NOTIFICATION_TIME, response.dailyNotificationTime());
        verify(userSettingsRepository).save(settings);
    }

    @Test
    void shouldThrowNotFoundWhenUpdatingSettingsThatDoNotExist() {
        UpdateUserSettingsRequest request =
                new UpdateUserSettingsRequest(
                        UPDATED_TIMEZONE, null, null, null, null, null, null, null, null);
        when(userSettingsRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        NotFoundException exception =
                assertThrows(
                        NotFoundException.class,
                        () -> userSettingsService.updateSettings(user, request));

        assertTrue(exception.getMessage().contains(USER_ID.toString()));
        verify(userSettingsRepository, never()).save(settings);
    }
}
