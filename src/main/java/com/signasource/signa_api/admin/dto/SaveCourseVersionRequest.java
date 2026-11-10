package com.signasource.signa_api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SaveCourseVersionRequest(
        @NotBlank @Size(max = 150) String name,
        String description,
        boolean isFree,
        BigDecimal price,
        List<TopicData> topics) {

    public record TopicData(
            UUID id,
            @NotBlank @Size(max = 50) String title,
            @Size(max = 150) String subtitle,
            String description,
            List<LessonData> lessons) {}

    public record LessonData(
            UUID id,
            @NotBlank @Size(max = 150) String name,
            String description,
            List<BlockData> blocks) {}

    public record BlockData(
            UUID id, @NotBlank String type, Integer xpReward, Map<String, Object> config) {}
}
