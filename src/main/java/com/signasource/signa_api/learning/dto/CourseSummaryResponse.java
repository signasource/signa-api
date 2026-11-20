package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.Course;
import java.math.BigDecimal;
import java.util.UUID;

public record CourseSummaryResponse(
        UUID id,
        String name,
        String description,
        String coverUrl,
        boolean isFree,
        BigDecimal price,
        boolean owned,
        String signLanguageCode) {
    /** For contexts where individual ownership does not apply (e.g. organization listings). */
    public static CourseSummaryResponse from(Course course) {
        return from(course, false);
    }

    public static CourseSummaryResponse from(Course course, boolean owned) {
        return new CourseSummaryResponse(
                course.getId(),
                course.getName(),
                course.getDescription(),
                course.getCoverUrl(),
                course.isFree(),
                course.getPrice(),
                owned,
                course.getSignLanguage().getCode());
    }
}
