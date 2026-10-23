package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.VersionStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminCourseResponse(
        UUID id,
        String code,
        String name,
        boolean isFree,
        String signLanguageCode,
        long enrollmentCount,
        List<VersionSummary> versions) {

    public record VersionSummary(
            UUID id, String version, VersionStatus status, Instant publishedAt) {}

    public static AdminCourseResponse from(Course course, long enrollmentCount) {
        List<VersionSummary> versions =
                course.getVersions().stream()
                        .sorted(
                                (a, b) ->
                                        b.getPublishedAt() == null
                                                ? -1
                                                : a.getPublishedAt() == null
                                                        ? 1
                                                        : b.getPublishedAt()
                                                                .compareTo(a.getPublishedAt()))
                        .map(
                                v ->
                                        new VersionSummary(
                                                v.getId(),
                                                v.getVersion(),
                                                v.getStatus(),
                                                v.getPublishedAt()))
                        .toList();
        return new AdminCourseResponse(
                course.getId(),
                course.getCode(),
                course.getName(),
                course.isFree(),
                course.getSignLanguage().getCode(),
                enrollmentCount,
                versions);
    }
}
