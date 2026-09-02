package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.Topic;
import java.util.UUID;

public record TopicSummaryResponse(
        UUID id, String code, String title, String subtitle, String coverUrl, int order) {
    public static TopicSummaryResponse from(Topic topic) {
        return new TopicSummaryResponse(
                topic.getId(),
                topic.getCode(),
                topic.getTitle(),
                topic.getSubtitle(),
                topic.getCoverUrl(),
                topic.getOrder());
    }
}
