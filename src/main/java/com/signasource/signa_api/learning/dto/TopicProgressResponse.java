package com.signasource.signa_api.learning.dto;

public record TopicProgressResponse(
        String title, int totalLessons, int completedLessons, int progressPercentage) {}
