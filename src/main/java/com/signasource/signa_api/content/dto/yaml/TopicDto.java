package com.signasource.signa_api.content.dto.yaml;

import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.Topic;

public record TopicDto(String code, String name, String description, String cover) {

    public Topic toEntity(int order, CourseVersion courseVersion) {
        return Topic.builder()
                .code(code)
                .name(name)
                .description(description)
                .coverUrl(cover)
                .order(order)
                .courseVersion(courseVersion)
                .build();
    }
}
