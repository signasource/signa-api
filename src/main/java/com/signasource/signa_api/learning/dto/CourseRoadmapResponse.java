package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import java.util.List;
import java.util.UUID;

public record CourseRoadmapResponse(
        UUID courseId, String courseName, String activeVersion, List<RoadmapTopicResponse> topics) {

    public static CourseRoadmapResponse of(
            Course course, CourseVersion version, List<RoadmapTopicResponse> topics) {
        return new CourseRoadmapResponse(
                course.getId(), course.getName(), version.getVersion(), topics);
    }
}
