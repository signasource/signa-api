package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.Lesson;
import java.util.List;
import java.util.UUID;

public record RoadmapLessonResponse(
        UUID id,
        String code,
        String name,
        String description,
        int order,
        int blockCount,
        int xpTotal,
        int signsCount,
        List<String> signsLearned,
        LessonRoadmapState state) {

    public static RoadmapLessonResponse of(
            Lesson lesson,
            int blockCount,
            int xpTotal,
            List<String> signsLearned,
            LessonRoadmapState state) {
        return new RoadmapLessonResponse(
                lesson.getId(),
                lesson.getCode(),
                lesson.getName(),
                lesson.getDescription(),
                lesson.getOrder(),
                blockCount,
                xpTotal,
                signsLearned.size(),
                signsLearned,
                state);
    }
}
