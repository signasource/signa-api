package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import java.time.Instant;
import java.util.UUID;

public record EnrollmentSummaryResponse(
        UUID courseId,
        String courseName,
        String coverUrl,
        String organizationName,
        Instant accessExpiresAt,
        boolean isCurrent) {
    public static EnrollmentSummaryResponse from(
            UserCourseEnrollment enrollment, boolean isCurrent) {
        Course course = enrollment.getCourseVersion().getCourse();
        return new EnrollmentSummaryResponse(
                course.getId(),
                course.getName(),
                course.getCoverUrl(),
                enrollment.getOrganization() == null
                        ? null
                        : enrollment.getOrganization().getName(),
                enrollment.getAccessExpiresAt(),
                isCurrent);
    }
}
