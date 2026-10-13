package com.signasource.signa_api.organizations.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Team activity for a period, day by day in Argentina's calendar. {@code totals.activeParticipants}
 * counts distinct people over the whole period, so it is not the sum of the daily figures.
 */
public record OrganizationActivityResponse(
        LocalDate from, LocalDate to, ActivityFigures totals, List<DailyActivity> days) {

    public record ActivityFigures(
            long activeParticipants,
            long exerciseAttempts,
            long correctAnswers,
            int correctPercentage,
            long lessonsCompleted,
            long learningMinutes) {}

    public record DailyActivity(LocalDate date, ActivityFigures figures) {}
}
