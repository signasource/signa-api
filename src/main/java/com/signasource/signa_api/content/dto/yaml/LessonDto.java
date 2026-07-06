package com.signasource.signa_api.content.dto.yaml;

import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.Topic;
import java.util.List;

public record LessonDto(String code, String name, String description, List<LessonBlockDto> blocks) {

    public Lesson toEntity(int order, Topic topic) {
        return Lesson.builder()
                .code(code)
                .name(name)
                .description(description)
                .order(order)
                .topic(topic)
                .build();
    }
}
