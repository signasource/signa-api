package com.signasource.signa_api.notification.dto;

import java.time.Instant;
import java.util.Map;

import com.signasource.signa_api.notification.entity.NotificationCode;
import com.signasource.signa_api.notification.entity.NotificationHistory;

public record NotificationResponse(Long id, NotificationCode code, String title, String body, boolean read,
		Instant sentAt, Instant readAt, Map<String, String> metadata) {

	public static NotificationResponse from(NotificationHistory history) {
		return new NotificationResponse(history.getId(), history.getTemplate().getCode(), history.getTitle(),
				history.getBody(), history.isRead(), history.getSentAt(), history.getReadAt(), history.getMetadata());
	}
}
