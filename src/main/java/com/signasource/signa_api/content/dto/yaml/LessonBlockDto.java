package com.signasource.signa_api.content.dto.yaml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.content.exception.ContentLoadException;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;

public record LessonBlockDto(BlockType type, Integer xp, JsonNode config) {

    public LessonBlock toEntity(int order, Lesson lesson, ObjectMapper objectMapper) {
        return LessonBlock.builder()
                .type(type)
                .order(order)
                .config(serializeConfig(objectMapper))
                .xpReward(xp)
                .lesson(lesson)
                .build();
    }

    private String serializeConfig(ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new ContentLoadException("Failed to serialize block config", e);
        }
    }
}
