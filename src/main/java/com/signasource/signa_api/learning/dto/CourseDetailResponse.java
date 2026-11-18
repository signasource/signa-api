package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Detail of a course. Enrolled users see the version they are subscribed to; non-enrolled users see
 * the currently published version.
 *
 * @param signCount distinct signs taught across the whole course
 * @param hasCameraPractice whether any exercise uses on-device sign recognition
 * @param hasCameraRecognition explicitly declared in the course YAML
 * @param hasAnimations whether the course uses 3D animated avatars
 * @param hasVideos whether the course uses video recordings
 * @param exerciseTypes distinct exercise block types the course uses (never {@code INFO} or {@code
 *     INTRODUCE_SIGN})
 * @param sampleSign meaning of the first sign taught, or {@code null} if the course teaches none
 */
public record CourseDetailResponse(
        UUID id,
        String name,
        String description,
        String coverUrl,
        boolean isFree,
        BigDecimal price,
        boolean owned,
        String activeVersion,
        int unitCount,
        int lessonCount,
        int signCount,
        boolean hasCameraPractice,
        boolean hasCameraRecognition,
        boolean hasAnimations,
        boolean hasVideos,
        List<BlockType> exerciseTypes,
        String sampleSign,
        List<TopicSummaryResponse> topics) {
    public static CourseDetailResponse from(
            Course course,
            CourseVersion activeVersion,
            boolean owned,
            CourseContentStats stats,
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
                topics.size(),
                stats.lessonCount(),
                stats.signCount(),
                stats.hasCameraPractice(),
                course.isHasCameraRecognition(),
                "3D".equals(course.getMediaFormat()),
                "VIDEO".equals(course.getMediaFormat()),
                stats.exerciseTypes(),
                stats.sampleSign(),
                topics);
    }

    /** Aggregates over a course version's content, computed once by the service. */
    public record CourseContentStats(
            int lessonCount,
            int signCount,
            boolean hasCameraPractice,
            List<BlockType> exerciseTypes,
            String sampleSign) {}
}
