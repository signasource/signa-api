package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Public detail of a course (also for users who don't own it yet): what it contains, computed from
 * the published version's content.
 *
 * @param signCount distinct signs taught across the whole course
 * @param hasCameraPractice whether any exercise uses on-device sign recognition
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
