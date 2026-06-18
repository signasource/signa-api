package com.signasource.signa_api.learning.dto;

import java.util.UUID;
import com.signasource.signa_api.learning.entity.Topic;

public record TopicSummaryResponse(UUID id, String code, String name, String coverUrl, int order) {
	public static TopicSummaryResponse from(Topic topic) {
		return new TopicSummaryResponse(topic.getId(), topic.getCode(), topic.getName(), topic.getCoverUrl(),
				topic.getOrder());
	}
}
