package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.LessonBlock;
import java.util.UUID;

public record PracticeMistakeResponse(UUID lessonBlockId, String type, int misses) {
    public static PracticeMistakeResponse from(LessonBlock block, int misses) {
        return new PracticeMistakeResponse(block.getId(), block.getType().name(), misses);
    }
}
