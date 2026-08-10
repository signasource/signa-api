package com.signasource.signa_api.users.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.users.dto.DailyGoalResponse;
import com.signasource.signa_api.users.dto.UpdateDailyGoalRequest;
import com.signasource.signa_api.users.dto.UpdateUserSettingsRequest;
import com.signasource.signa_api.users.dto.UserSettingsResponse;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserSettings;
import com.signasource.signa_api.users.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserSettingsService {
    private final UserSettingsRepository userSettingsRepository;

    @Transactional(readOnly = true)
    public UserSettingsResponse getSettings(User user) {
        return UserSettingsResponse.from(getSettingsEntity(user));
    }

    @Transactional
    public UserSettingsResponse updateSettings(User user, UpdateUserSettingsRequest request) {
        UserSettings settings = getSettingsEntity(user);

        applyChanges(settings, request);
        userSettingsRepository.save(settings);

        return UserSettingsResponse.from(settings);
    }

    @Transactional(readOnly = true)
    public DailyGoalResponse getDailyGoal(User user) {
        return DailyGoalResponse.from(getSettingsEntity(user));
    }

    @Transactional
    public DailyGoalResponse updateDailyGoal(User user, UpdateDailyGoalRequest request) {
        UserSettings settings = getSettingsEntity(user);

        settings.setDailyGoalMinutes(request.dailyGoalMinutes());
        userSettingsRepository.save(settings);

        return DailyGoalResponse.from(settings);
    }

    private UserSettings getSettingsEntity(User user) {
        return userSettingsRepository
                .findByUserId(user.getId())
                .orElseThrow(
                        () ->
                                new NotFoundException(
                                        "Settings not found for user ID: " + user.getId()));
    }

    private void applyChanges(UserSettings settings, UpdateUserSettingsRequest request) {
        if (request.timezone() != null) {
            settings.setTimezone(request.timezone());
        }
        if (request.notificationsEnabled() != null) {
            settings.setNotificationsEnabled(request.notificationsEnabled());
        }
        if (request.theme() != null) {
            settings.setTheme(request.theme());
        }
        if (request.fontSize() != null) {
            settings.setFontSize(request.fontSize());
        }
        if (request.vibrationEnabled() != null) {
            settings.setVibrationEnabled(request.vibrationEnabled());
        }
        if (request.accountVisibility() != null) {
            settings.setAccountVisibility(request.accountVisibility());
        }
        if (request.dailyGoalMinutes() != null) {
            settings.setDailyGoalMinutes(request.dailyGoalMinutes());
        }
        if (request.dailyNotificationEnabled() != null) {
            settings.setDailyNotificationEnabled(request.dailyNotificationEnabled());
        }
        if (request.dailyNotificationTime() != null) {
            settings.setDailyNotificationTime(request.dailyNotificationTime());
        }
    }
}
