package com.signasource.signa_api.content.dto.yaml;

import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.VersionStatus;
import java.time.Instant;

public record CourseVersionDto(String version, VersionStatus status) {

    public CourseVersion toEntity(Course course) {
        Instant publishedAt = status == VersionStatus.PUBLISHED ? Instant.now() : null;
        return CourseVersion.builder()
                .version(version)
                .status(status)
                .publishedAt(publishedAt)
                .course(course)
                .build();
    }
}
