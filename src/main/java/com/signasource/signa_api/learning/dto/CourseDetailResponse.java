package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CourseDetailResponse(
        UUID id,
        String name,
        String description,
        String coverUrl,
        boolean isFree,
        BigDecimal price,
        boolean owned,
        String activeVersion,
        List<TopicSummaryResponse> topics) {
    public static CourseDetailResponse from(
            Course course,
            CourseVersion activeVersion,
            boolean owned,
            List<TopicSummaryResponse> topics) {
        return new CourseDetailResponse(
                course.getId(),
                course.getName(),
                course.getDescription(),
                course.getCoverUrl(),
                course.isFree(),
                course.getPrice(),
                owned,
                activeVersion.getVersion(),
                topics);
    }
}
