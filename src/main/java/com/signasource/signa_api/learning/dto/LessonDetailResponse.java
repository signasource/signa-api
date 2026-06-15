package com.signasource.signa_api.learning.dto;

import java.util.List;
import java.util.UUID;

public record LessonDetailResponse(
    UUID id,
    String name,
    String description,
    int order,
    List<LessonBlockResponse> blocks
) {}
