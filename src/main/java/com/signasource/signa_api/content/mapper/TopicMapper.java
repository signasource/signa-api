package com.signasource.signa_api.content.mapper;

import com.signasource.signa_api.content.dto.TopicDto;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.Topic;
import org.springframework.stereotype.Component;

@Component
public class TopicMapper {

    public Topic toEntity(TopicDto dto, int order, CourseVersion courseVersion) {
        return Topic.builder()
                .code(dto.code())
                .name(dto.name())
                .description(dto.description())
                .coverUrl(dto.cover())
                .order(order)
                .courseVersion(courseVersion)
                .build();
    }
}
