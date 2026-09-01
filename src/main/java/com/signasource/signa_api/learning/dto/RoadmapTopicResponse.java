package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.Topic;
import java.util.List;
import java.util.UUID;

public record RoadmapTopicResponse(
        UUID id,
        String code,
        String name,
        String description,
        int order,
        List<RoadmapLessonResponse> lessons) {

    public static RoadmapTopicResponse of(Topic topic, List<RoadmapLessonResponse> lessons) {
        return new RoadmapTopicResponse(
                topic.getId(),
                topic.getCode(),
                topic.getName(),
                topic.getDescription(),
                topic.getOrder(),
                lessons);
    }
}
