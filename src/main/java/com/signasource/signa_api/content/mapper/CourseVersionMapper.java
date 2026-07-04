package com.signasource.signa_api.content.mapper;

import com.signasource.signa_api.content.dto.CourseVersionDto;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import org.springframework.stereotype.Component;

@Component
public class CourseVersionMapper {

    public CourseVersion toEntity(CourseVersionDto dto, Course course) {
        return CourseVersion.builder()
                .version(dto.version())
                .status(dto.status())
                .course(course)
                .build();
    }
}
