package com.signasource.signa_api.content.mapper;

import com.signasource.signa_api.content.dto.LessonDto;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.Topic;
import org.springframework.stereotype.Component;

@Component
public class LessonMapper {

    public Lesson toEntity(LessonDto dto, int order, Topic topic) {
        return Lesson.builder()
                .code(dto.code())
                .name(dto.name())
                .description(dto.description())
                .order(order)
                .topic(topic)
                .build();
    }
}
