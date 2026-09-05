package com.signasource.signa_api.users.dto;

import com.signasource.signa_api.users.entity.UserSettings;

public record DailyGoalResponse(int dailyGoalMinutes, int minutesToday) {
    public static DailyGoalResponse from(UserSettings settings, int minutesToday) {
        return new DailyGoalResponse(settings.getDailyGoalMinutes(), minutesToday);
    }
}
