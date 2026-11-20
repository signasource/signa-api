package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import java.time.Instant;

public record CourseProgressResponse(
        String courseName,
        EnrollmentStatus status,
        int totalLessons,
        int completedLessons,
        int progressPercentage,
        int signsLearned,
        TopicProgressResponse currentTopic,
        Instant accessExpiresAt,
        boolean accessExpired) {}
