package com.signasource.signa_api.learning.dto;

import java.util.UUID;

public record TopicSummaryResponse(
    UUID id,
    String code,
    String name,
    String coverUrl,
    int order
) {}
