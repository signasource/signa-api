package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.LessonBlock;
import java.util.UUID;

public record LessonBlockResponse(
        UUID id, String type, int order, String config, Integer xpReward) {
    public static LessonBlockResponse from(LessonBlock block) {
        return new LessonBlockResponse(
                block.getId(),
                block.getType().name(),
                block.getOrder(),
                block.getConfig(),
                block.getXpReward());
    }
}
