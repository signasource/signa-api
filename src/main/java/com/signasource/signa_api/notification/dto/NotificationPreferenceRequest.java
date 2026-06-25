package com.signasource.signa_api.notification.dto;

import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;

public record NotificationPreferenceRequest(LocalTime dailyReminderTime, @NotBlank String timezone,
		boolean dailyReminderEnabled) {
}
