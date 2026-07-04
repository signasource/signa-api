package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import java.util.List;
import java.util.UUID;

public record CourseDetailResponse(
        UUID id,
        String name,
        String description,
        String coverUrl,
        String activeVersion,
        List<TopicSummaryResponse> topics) {
    public static CourseDetailResponse from(
            Course course, CourseVersion activeVersion, List<TopicSummaryResponse> topics) {
        return new CourseDetailResponse(
                course.getId(),
                course.getName(),
                course.getDescription(),
                course.getCoverUrl(),
                activeVersion.getVersion(),
                topics);
    }
}
