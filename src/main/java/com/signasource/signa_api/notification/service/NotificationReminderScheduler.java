package com.signasource.signa_api.notification.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.signasource.signa_api.notification.entity.NotificationPreference;
import com.signasource.signa_api.notification.repository.NotificationPreferenceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Polls every 15 minutes and sends the daily reminder to users whose chosen
 * local time falls in the current slot. Matching is done in each user's
 * timezone, and {@code lastReminderSentOn} (checked here and again inside the
 * processor transaction) keeps it to at most once per local day. A polling job
 * is the right fit for a single monolith — no external scheduler, queue or
 * cache required.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationReminderScheduler {

	private static final int SLOT_MINUTES = 15;

	private final NotificationPreferenceRepository preferenceRepository;
	private final DailyReminderProcessor reminderProcessor;

	@Scheduled(cron = "0 */15 * * * *")
	public void sendDailyReminders() {
		Instant now = Instant.now();
		for (NotificationPreference preference : preferenceRepository.findByDailyReminderEnabledTrue()) {
			try {
				ZonedDateTime localNow = now.atZone(ZoneId.of(preference.getTimezone()));
				LocalDate today = localNow.toLocalDate();
				if (isDue(preference, today, localNow.toLocalTime())) {
					reminderProcessor.process(preference.getId(), today);
				}
			} catch (Exception e) {
				log.error("Failed to process daily reminder for preference {}", preference.getId(), e);
			}
		}
	}

	private boolean isDue(NotificationPreference preference, LocalDate today, LocalTime localNow) {
		if (preference.getDailyReminderTime() == null || today.equals(preference.getLastReminderSentOn())) {
			return false;
		}
		return isWithinCurrentSlot(preference.getDailyReminderTime(), localNow);
	}

	private boolean isWithinCurrentSlot(LocalTime reminderTime, LocalTime localNow) {
		int nowMinutes = localNow.toSecondOfDay() / 60;
		int slotStart = (nowMinutes / SLOT_MINUTES) * SLOT_MINUTES;
		int reminderMinutes = reminderTime.toSecondOfDay() / 60;
		return reminderMinutes >= slotStart && reminderMinutes < slotStart + SLOT_MINUTES;
	}
}
