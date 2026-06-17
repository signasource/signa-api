package com.signasource.signa_api.learning.dto;

import java.util.UUID;
import com.signasource.signa_api.learning.entity.LessonBlock;

public record LessonBlockResponse(
    UUID id,
    String type,
    int order,
    String config,
    int xpReward,
    boolean isExamEligible
) {
    public static LessonBlockResponse from(LessonBlock block) {
        return new LessonBlockResponse(
            block.getId(),
            block.getType().name(),
            block.getOrder(),
            block.getConfig(),
            block.getXpReward(),
            block.isExamEligible()
        );
    }
}
