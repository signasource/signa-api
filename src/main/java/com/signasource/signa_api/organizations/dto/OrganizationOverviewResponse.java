package com.signasource.signa_api.organizations.dto;

import java.util.List;

/**
 * Dashboard figures over the organization's active participants (admins excluded). A participant is
 * "active" when they made an attempt on contracted content in the last {@code activeWindowDays}.
 */
public record OrganizationOverviewResponse(
        Participation participation, Progress progress, Performance performance) {

    public record Participation(
            long totalParticipants,
            long activeParticipants,
            long inactiveParticipants,
            long participantsStarted,
            int activeWindowDays,
            double averageActiveDaysLast30,
            long participantsWithStreak,
            int longestCurrentStreak) {}

    public record Progress(
            int averageProgressPercentage,
            long completedLessons,
            long pendingLessons,
            long modulesCompleted,
            long participantsCompletedAll,
            long totalLearningMinutes,
            double averageLearningMinutes) {}

    public record Performance(
            long exerciseAttempts,
            int correctPercentage,
            long signRecognitionAttempts,
            int signRecognitionCorrectPercentage,
            List<WeeklyPerformanceResponse> weeklyEvolution) {}
}
