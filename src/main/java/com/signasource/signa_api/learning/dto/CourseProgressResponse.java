package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import java.time.Instant;
import java.util.UUID;

/** A learner's progress in one course (one row per course, in the version they take it). */
public record CourseProgressResponse(
        UUID courseId,
        String courseName,
        EnrollmentStatus status,
        int totalLessons,
        int completedLessons,
        int progressPercentage,
        int signsLearned,
        TopicProgressResponse currentTopic,
        Instant accessExpiresAt,
        boolean accessExpired) {}
