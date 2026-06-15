package com.signasource.signa_api.learning.dto;

import java.util.UUID;

public record CourseSummaryResponse(UUID id, String name, String description, String coverUrl, boolean isFree,
		String signLanguageCode) {
}
