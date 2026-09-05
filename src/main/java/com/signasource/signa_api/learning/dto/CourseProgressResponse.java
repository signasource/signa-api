package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.EnrollmentStatus;

public record CourseProgressResponse(
        String courseName,
        EnrollmentStatus status,
        int totalLessons,
        int completedLessons,
        int progressPercentage,
        int signsLearned,
        TopicProgressResponse currentTopic) {}
