package com.signasource.signa_api.organizations.dto;

import java.util.UUID;

public record ModuleStatsResponse(
        UUID courseId,
        String courseName,
        UUID topicId,
        String title,
        int order,
        long totalLessons,
        long participantsCompleted,
        long participantsInProgress,
        int completionPercentage,
        long exerciseAttempts,
        int correctPercentage) {}
