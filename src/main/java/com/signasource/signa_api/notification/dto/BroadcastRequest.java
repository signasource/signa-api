package com.signasource.signa_api.notification.dto;

import com.signasource.signa_api.notification.entity.NotificationCode;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BroadcastRequest(@NotNull NotificationCode code, @Size(max = 120) String title,
		@Size(max = 1000) String body) {
}
