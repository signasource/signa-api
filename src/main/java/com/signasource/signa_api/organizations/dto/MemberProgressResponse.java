package com.signasource.signa_api.organizations.dto;

import com.signasource.signa_api.organizations.entity.MemberStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Attempt figures only count activity on the organization's contracted courses. Learning minutes
 * and active days come from the user's daily activity log, which is not split by organization.
 */
public record MemberProgressResponse(
        UUID userId,
        String name,
        String lastName,
        String email,
        MemberStatus status,
        Instant joinedAt,
        Instant lastActivityAt,
        int progressPercentage,
        long completedLessons,
        long totalLessons,
        long modulesCompleted,
        String currentModule,
        long exerciseAttempts,
        long correctAnswers,
        int correctPercentage,
        long signsLearned,
        int currentStreak,
        long learningMinutes,
        long activeDaysLast30,
        List<MemberCourseProgressResponse> courses) {}
