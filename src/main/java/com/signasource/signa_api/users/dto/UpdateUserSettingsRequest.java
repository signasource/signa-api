package com.signasource.signa_api.users.dto;

import com.signasource.signa_api.users.entity.AccountVisibility;
import com.signasource.signa_api.users.entity.FontSize;
import com.signasource.signa_api.users.entity.Theme;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;

public record UpdateUserSettingsRequest(
        @Size(max = 64) String timezone,
        Boolean notificationsEnabled,
        Theme theme,
        FontSize fontSize,
        Boolean vibrationEnabled,
        AccountVisibility accountVisibility,
        @Min(1) @Max(1440) Integer dailyGoalMinutes,
        Boolean dailyNotificationEnabled,
        LocalTime dailyNotificationTime) {}
