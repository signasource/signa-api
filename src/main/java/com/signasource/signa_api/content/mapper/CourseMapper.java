package com.signasource.signa_api.content.mapper;

import com.signasource.signa_api.content.dto.CourseMetadataDto;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.SignLanguage;
import org.springframework.stereotype.Component;

@Component
public class CourseMapper {

    public Course toEntity(CourseMetadataDto dto, SignLanguage signLanguage) {
        return Course.builder()
                .code(dto.code())
                .name(dto.name())
                .description(dto.description())
                .isFree(dto.free())
                .coverUrl(dto.cover())
                .signLanguage(signLanguage)
                .build();
    }
}
