package com.signasource.signa_api.notification.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.signasource.signa_api.notification.entity.NotificationPreference;
import com.signasource.signa_api.notification.repository.NotificationPreferenceRepository;
import com.signasource.signa_api.users.entity.User;

@ExtendWith(MockitoExtension.class)
class NotificationReminderSchedulerTest {

	@Mock
	private NotificationPreferenceRepository preferenceRepository;
	@Mock
	private DailyReminderProcessor reminderProcessor;

	@InjectMocks
	private NotificationReminderScheduler scheduler;

	private final User user = User.builder().id(UUID.randomUUID()).email("a@b.com").build();

	@Test
	void processesReminderDueInCurrentSlot() {
		LocalTime nowUtc = Instant.now().atZone(ZoneId.of("UTC")).toLocalTime();
		int slotStartMinutes = (nowUtc.toSecondOfDay() / 60 / 15) * 15;
		LocalTime reminderTime = LocalTime.of(slotStartMinutes / 60, slotStartMinutes % 60);

		NotificationPreference pref = NotificationPreference.builder().id(1L).user(user).timezone("UTC")
				.dailyReminderEnabled(true).dailyReminderTime(reminderTime).build();
		when(preferenceRepository.findByDailyReminderEnabledTrue()).thenReturn(List.of(pref));

		scheduler.sendDailyReminders();

		verify(reminderProcessor).process(eq(1L), any(LocalDate.class));
	}

	@Test
	void skipsReminderAlreadySentToday() {
		LocalDate todayUtc = LocalDate.now(ZoneId.of("UTC"));
		NotificationPreference pref = NotificationPreference.builder().id(1L).user(user).timezone("UTC")
				.dailyReminderEnabled(true).dailyReminderTime(LocalTime.of(12, 0)).lastReminderSentOn(todayUtc).build();
		when(preferenceRepository.findByDailyReminderEnabledTrue()).thenReturn(List.of(pref));

		scheduler.sendDailyReminders();

		verify(reminderProcessor, never()).process(any(), any());
	}

	@Test
	void handlesInvalidTimezoneGracefully() {
		NotificationPreference pref = NotificationPreference.builder().id(1L).user(user).timezone("Invalid/Zone")
				.dailyReminderEnabled(true).dailyReminderTime(LocalTime.of(12, 0)).build();
		when(preferenceRepository.findByDailyReminderEnabledTrue()).thenReturn(List.of(pref));

		assertDoesNotThrow(() -> scheduler.sendDailyReminders());
		verify(reminderProcessor, never()).process(any(), any());
	}
}
