package com.signasource.signa_api.learning.dto;

import java.util.List;
import java.util.UUID;

public record CourseDetailResponse(UUID id, String name, String description, String coverUrl, String activeVersion,
		List<TopicSummaryResponse> topics) {
}
