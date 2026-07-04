package com.signasource.signa_api.content.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.signasource.signa_api.learning.entity.BlockType;

public record LessonBlockDto(BlockType type, Integer xp, JsonNode config) {}
