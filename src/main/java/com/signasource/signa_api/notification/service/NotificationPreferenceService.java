package com.signasource.signa_api.notification.service;

import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.notification.dto.NotificationPreferenceRequest;
import com.signasource.signa_api.notification.dto.NotificationPreferenceResponse;
import com.signasource.signa_api.notification.entity.NotificationPreference;
import com.signasource.signa_api.notification.repository.NotificationPreferenceRepository;
import com.signasource.signa_api.users.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

	private final NotificationPreferenceRepository preferenceRepository;

	@Value("${app.default-timezone:America/Argentina/Buenos_Aires}")
	private String defaultTimezone;

	@Transactional
	public NotificationPreferenceResponse getOrCreate(User user) {
		return NotificationPreferenceResponse.from(getOrCreateEntity(user));
	}

	@Transactional
	public NotificationPreferenceResponse update(User user, NotificationPreferenceRequest request) {
		validateTimezone(request.timezone());
		if (request.dailyReminderEnabled() && request.dailyReminderTime() == null) {
			throw new InvalidInputException("A daily reminder time is required when the reminder is enabled");
		}

		NotificationPreference preference = getOrCreateEntity(user);
		preference.setTimezone(request.timezone());
		preference.setDailyReminderTime(request.dailyReminderTime());
		preference.setDailyReminderEnabled(request.dailyReminderEnabled());
		return NotificationPreferenceResponse.from(preferenceRepository.save(preference));
	}

	private NotificationPreference getOrCreateEntity(User user) {
		return preferenceRepository.findByUserId(user.getId())
				.orElseGet(() -> preferenceRepository.save(NotificationPreference.builder().user(user)
						.timezone(defaultTimezone).dailyReminderEnabled(false).build()));
	}

	private void validateTimezone(String timezone) {
		if (timezone == null || !ZoneId.getAvailableZoneIds().contains(timezone)) {
			throw new InvalidInputException("Invalid timezone: " + timezone);
		}
	}
}
