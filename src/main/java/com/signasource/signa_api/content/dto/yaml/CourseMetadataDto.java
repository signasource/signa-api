package com.signasource.signa_api.content.dto.yaml;

import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.SignLanguage;

public record CourseMetadataDto(
        String code, String name, String description, boolean free, String cover) {

    public Course toEntity(SignLanguage signLanguage) {
        return Course.builder()
                .code(code)
                .name(name)
                .description(description)
                .isFree(free)
                .coverUrl(cover)
                .signLanguage(signLanguage)
                .build();
    }
}
