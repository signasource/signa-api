package com.signasource.signa_api.content.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.content.dto.LessonBlockDto;
import com.signasource.signa_api.content.exception.ContentLoadException;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import org.springframework.stereotype.Component;

@Component
public class LessonBlockMapper {

    private final ObjectMapper objectMapper;

    public LessonBlockMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LessonBlock toEntity(LessonBlockDto dto, int order, Lesson lesson) {
        return LessonBlock.builder()
                .type(dto.type())
                .order(order)
                .config(serializeConfig(dto.config()))
                .xpReward(dto.xp())
                .lesson(lesson)
                .build();
    }

    private String serializeConfig(JsonNode config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            throw new ContentLoadException("Failed to serialize block config", e);
        }
    }
}
