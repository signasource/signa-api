package com.signasource.signa_api.learning.dto;

import java.util.List;
import java.util.UUID;
import com.signasource.signa_api.learning.entity.Lesson;

public record LessonDetailResponse(
    UUID id,
    String name,
    String description,
    int order,
    List<LessonBlockResponse> blocks
) {
    public static LessonDetailResponse from(Lesson lesson, List<LessonBlockResponse> blocks) {
        return new LessonDetailResponse(
            lesson.getId(),
            lesson.getName(),
            lesson.getDescription(),
            lesson.getOrder(),
            blocks
        );
    }
}
