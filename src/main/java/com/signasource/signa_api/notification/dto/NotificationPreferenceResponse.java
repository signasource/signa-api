package com.signasource.signa_api.notification.dto;

import java.time.LocalTime;

import com.signasource.signa_api.notification.entity.NotificationPreference;

public record NotificationPreferenceResponse(LocalTime dailyReminderTime, String timezone,
		boolean dailyReminderEnabled) {

	public static NotificationPreferenceResponse from(NotificationPreference preference) {
		return new NotificationPreferenceResponse(preference.getDailyReminderTime(), preference.getTimezone(),
				preference.isDailyReminderEnabled());
	}
}
