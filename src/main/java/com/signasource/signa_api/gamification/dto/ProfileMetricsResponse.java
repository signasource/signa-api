package com.signasource.signa_api.gamification.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Profile metrics for an inclusive date range. {@code daysGoalReached} compares each day's minutes
 * with the <em>current</em> daily goal (the goal history is not stored).
 */
public record ProfileMetricsResponse(
        LocalDate from,
        LocalDate to,
        int totalDays,
        int xpEarned,
        int signsLearned,
        int daysGoalReached,
        int dailyGoalMinutes,
        List<Day> days) {

    public record Day(LocalDate date, int xpEarned, int minutes, boolean goalReached) {}
}
