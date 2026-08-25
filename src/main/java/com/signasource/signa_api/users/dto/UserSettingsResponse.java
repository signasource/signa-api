package com.signasource.signa_api.users.dto;

import com.signasource.signa_api.users.entity.AccountVisibility;
import com.signasource.signa_api.users.entity.FontSize;
import com.signasource.signa_api.users.entity.Theme;
import com.signasource.signa_api.users.entity.UserSettings;
import java.time.LocalTime;

public record UserSettingsResponse(
        String timezone,
        boolean notificationsEnabled,
        Theme theme,
        FontSize fontSize,
        boolean vibrationEnabled,
        AccountVisibility accountVisibility,
        int dailyGoalMinutes,
        boolean dailyNotificationEnabled,
        LocalTime dailyNotificationTime,
        String profileHeaderColor) {
    public static UserSettingsResponse from(UserSettings settings) {
        return new UserSettingsResponse(
                settings.getTimezone(),
                settings.isNotificationsEnabled(),
                settings.getTheme(),
                settings.getFontSize(),
                settings.isVibrationEnabled(),
                settings.getAccountVisibility(),
                settings.getDailyGoalMinutes(),
                settings.isDailyNotificationEnabled(),
                settings.getDailyNotificationTime(),
                settings.getProfileHeaderColor());
    }
}
