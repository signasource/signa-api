package com.signasource.signa_api.content.dto.yaml;

import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.SignLanguage;
import java.math.BigDecimal;

public record CourseMetadataDto(
        String code,
        String name,
        String description,
        boolean free,
        BigDecimal price,
        String cover) {

    public Course toEntity(SignLanguage signLanguage) {
        return Course.builder()
                .code(code)
                .name(name)
                .description(description)
                .isFree(free)
                .price(free ? null : price)
                .coverUrl(cover)
                .signLanguage(signLanguage)
                .build();
    }
}
