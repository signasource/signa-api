package com.signasource.signa_api.content.dto.yaml;

import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.Topic;

public record TopicDto(
        String code, String title, String subtitle, String description, String cover) {

    public Topic toEntity(int order, CourseVersion courseVersion) {
        return Topic.builder()
                .code(code)
                .title(title)
                .subtitle(subtitle)
                .description(description)
                .coverUrl(cover)
                .order(order)
                .courseVersion(courseVersion)
                .build();
    }
}
