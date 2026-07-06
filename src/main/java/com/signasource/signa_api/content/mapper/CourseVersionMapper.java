package com.signasource.signa_api.content.mapper;

import com.signasource.signa_api.content.dto.CourseVersionDto;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.VersionStatus;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class CourseVersionMapper {

    public CourseVersion toEntity(CourseVersionDto dto, Course course) {
        Instant publishedAt = dto.status() == VersionStatus.PUBLISHED ? Instant.now() : null;
        return CourseVersion.builder()
                .version(dto.version())
                .status(dto.status())
                .publishedAt(publishedAt)
                .course(course)
                .build();
    }
}
