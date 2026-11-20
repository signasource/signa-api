package com.signasource.signa_api.admin.dto;

import java.util.List;
import java.util.UUID;

/**
 * A lesson that references a sign (in one or more of its blocks), with enough context to open it in
 * the course editor.
 */
public record SignUsageResponse(
        UUID courseId,
        String courseName,
        UUID courseVersionId,
        String version,
        String versionStatus,
        String topicTitle,
        UUID lessonId,
        String lessonName,
        List<String> blockTypes) {}
