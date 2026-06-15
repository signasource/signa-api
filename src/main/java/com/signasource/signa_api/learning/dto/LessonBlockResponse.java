package com.signasource.signa_api.learning.dto;

import java.util.UUID;

public record LessonBlockResponse(
    UUID id,
    String type,
    int order,
    String config,
    int xpReward,
    boolean isExamEligible
) {}
