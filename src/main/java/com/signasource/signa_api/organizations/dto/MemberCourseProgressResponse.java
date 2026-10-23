package com.signasource.signa_api.organizations.dto;

import java.util.UUID;

public record MemberCourseProgressResponse(
        UUID courseId,
        String courseName,
        long totalLessons,
        long completedLessons,
        int progressPercentage) {}
