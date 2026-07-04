package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.Course;
import java.util.UUID;

public record CourseSummaryResponse(
        UUID id,
        String name,
        String description,
        String coverUrl,
        boolean isFree,
        String signLanguageCode) {
    public static CourseSummaryResponse from(Course course) {
        return new CourseSummaryResponse(
                course.getId(),
                course.getName(),
                course.getDescription(),
                course.getCoverUrl(),
                course.isFree(),
                course.getSignLanguage().getCode());
    }
}
