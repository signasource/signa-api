package com.signasource.signa_api.users.dto;

import com.signasource.signa_api.users.entity.UserSettings;

public record DailyGoalResponse(int dailyGoalMinutes) {
    public static DailyGoalResponse from(UserSettings settings) {
        return new DailyGoalResponse(settings.getDailyGoalMinutes());
    }
}
