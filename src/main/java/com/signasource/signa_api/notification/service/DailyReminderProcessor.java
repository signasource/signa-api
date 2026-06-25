package com.signasource.signa_api.notification.service;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.signasource.signa_api.notification.entity.NotificationCode;
import com.signasource.signa_api.notification.entity.NotificationPreference;
import com.signasource.signa_api.notification.repository.NotificationPreferenceRepository;

import lombok.RequiredArgsConstructor;

/**
 * Sends one user's daily reminder inside its own transaction. Marking
 * {@code lastReminderSentOn} and persisting the notification happen together:
 * if delivery setup fails the transaction rolls back and the next slot retries;
 * on success the date is recorded so the reminder is not sent again today.
 */
@Service
@RequiredArgsConstructor
public class DailyReminderProcessor {

	private final NotificationPreferenceRepository preferenceRepository;
	private final NotificationService notificationService;

	@Transactional
	public void process(Long preferenceId, LocalDate localDate) {
		NotificationPreference preference = preferenceRepository.findById(preferenceId).orElse(null);
		if (preference == null || !preference.isDailyReminderEnabled()
				|| localDate.equals(preference.getLastReminderSentOn())) {
			return;
		}

		preference.setLastReminderSentOn(localDate);
		preferenceRepository.save(preference);
		notificationService.notifyUser(preference.getUser().getId(), NotificationCode.DAILY_REMINDER, Map.of());
	}
}
