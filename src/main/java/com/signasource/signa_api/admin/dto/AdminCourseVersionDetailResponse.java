package com.signasource.signa_api.admin.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.Topic;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AdminCourseVersionDetailResponse(
        UUID id,
        UUID courseId,
        String version,
        String status,
        Instant publishedAt,
        String basedOnVersion,
        String name,
        String description,
        boolean isFree,
        BigDecimal price,
        Instant updatedAt,
        String updatedBy,
        String color,
        List<TopicResponse> topics) {

    public record TopicResponse(
            UUID id,
            String title,
            String subtitle,
            String description,
            List<LessonResponse> lessons) {}

    public record LessonResponse(
            UUID id, String name, String description, List<BlockResponse> blocks) {}

    public record BlockResponse(
            UUID id, String type, int order, Integer xpReward, Map<String, Object> config) {}

    public static AdminCourseVersionDetailResponse from(CourseVersion v, ObjectMapper mapper) {
        List<TopicResponse> topics =
                v.getTopics().stream()
                        .sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
                        .map(t -> topicResponse(t, mapper))
                        .toList();
        return new AdminCourseVersionDetailResponse(
                v.getId(),
                v.getCourse().getId(),
                v.getVersion(),
                v.getStatus().name(),
                v.getPublishedAt(),
                v.getBasedOnVersion(),
                v.getCourse().getName(),
                v.getCourse().getDescription(),
                v.getCourse().isFree(),
                v.getCourse().getPrice(),
                v.getUpdatedAt(),
                v.getUpdatedBy(),
                v.getColor(),
                topics);
    }

    private static TopicResponse topicResponse(Topic t, ObjectMapper mapper) {
        List<LessonResponse> lessons =
                t.getLessons().stream()
                        .sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
                        .map(l -> lessonResponse(l, mapper))
                        .toList();
        return new TopicResponse(
                t.getId(), t.getTitle(), t.getSubtitle(), t.getDescription(), lessons);
    }

    private static LessonResponse lessonResponse(Lesson l, ObjectMapper mapper) {
        List<BlockResponse> blocks =
                l.getLessonBlocks().stream()
                        .sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
                        .map(b -> blockResponse(b, mapper))
                        .toList();
        return new LessonResponse(l.getId(), l.getName(), l.getDescription(), blocks);
    }

    private static BlockResponse blockResponse(LessonBlock b, ObjectMapper mapper) {
        Map<String, Object> config = parseConfig(b.getConfig(), mapper);
        return new BlockResponse(
                b.getId(), b.getType().name(), b.getOrder(), b.getXpReward(), config);
    }

    private static Map<String, Object> parseConfig(String json, ObjectMapper mapper) {
        if (json == null || json.isBlank()) return Collections.emptyMap();
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }
}
