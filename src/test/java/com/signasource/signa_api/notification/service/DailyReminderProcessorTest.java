package com.signasource.signa_api.notification.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.signasource.signa_api.notification.entity.NotificationCode;
import com.signasource.signa_api.notification.entity.NotificationPreference;
import com.signasource.signa_api.notification.repository.NotificationPreferenceRepository;
import com.signasource.signa_api.users.entity.User;

@ExtendWith(MockitoExtension.class)
class DailyReminderProcessorTest {

	@Mock
	private NotificationPreferenceRepository preferenceRepository;
	@Mock
	private NotificationService notificationService;

	@InjectMocks
	private DailyReminderProcessor processor;

	private final User user = User.builder().id(UUID.randomUUID()).email("a@b.com").build();
	private final LocalDate today = LocalDate.of(2026, 6, 24);

	@Test
	void processSendsReminderAndRecordsDate() {
		NotificationPreference pref = NotificationPreference.builder().id(1L).user(user).timezone("UTC")
				.dailyReminderEnabled(true).dailyReminderTime(LocalTime.of(20, 0)).build();
		when(preferenceRepository.findById(1L)).thenReturn(Optional.of(pref));

		processor.process(1L, today);

		verify(preferenceRepository).save(pref);
		verify(notificationService).notifyUser(eq(user.getId()), eq(NotificationCode.DAILY_REMINDER), eq(Map.of()));
		org.junit.jupiter.api.Assertions.assertEquals(today, pref.getLastReminderSentOn());
	}

	@Test
	void processSkipsWhenPreferenceMissing() {
		when(preferenceRepository.findById(1L)).thenReturn(Optional.empty());

		processor.process(1L, today);

		verify(preferenceRepository, never()).save(any());
		verify(notificationService, never()).notifyUser(any(), any(), any());
	}

	@Test
	void processSkipsWhenReminderDisabled() {
		NotificationPreference pref = NotificationPreference.builder().id(1L).user(user).timezone("UTC")
				.dailyReminderEnabled(false).build();
		when(preferenceRepository.findById(1L)).thenReturn(Optional.of(pref));

		processor.process(1L, today);

		verify(notificationService, never()).notifyUser(any(), any(), any());
	}

	@Test
	void processSkipsWhenAlreadySentToday() {
		NotificationPreference pref = NotificationPreference.builder().id(1L).user(user).timezone("UTC")
				.dailyReminderEnabled(true).dailyReminderTime(LocalTime.of(20, 0)).lastReminderSentOn(today).build();
		when(preferenceRepository.findById(1L)).thenReturn(Optional.of(pref));

		processor.process(1L, today);

		verify(notificationService, never()).notifyUser(any(), any(), any());
	}
}
