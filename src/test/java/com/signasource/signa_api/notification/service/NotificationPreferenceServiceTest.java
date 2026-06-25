package com.signasource.signa_api.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.notification.dto.NotificationPreferenceRequest;
import com.signasource.signa_api.notification.dto.NotificationPreferenceResponse;
import com.signasource.signa_api.notification.entity.NotificationPreference;
import com.signasource.signa_api.notification.repository.NotificationPreferenceRepository;
import com.signasource.signa_api.users.entity.User;

@ExtendWith(MockitoExtension.class)
class NotificationPreferenceServiceTest {

	@Mock
	private NotificationPreferenceRepository preferenceRepository;

	@InjectMocks
	private NotificationPreferenceService preferenceService;

	private User user;

	@BeforeEach
	void setUp() {
		user = User.builder().id(UUID.randomUUID()).email("a@b.com").build();
		ReflectionTestUtils.setField(preferenceService, "defaultTimezone", "UTC");
	}

	@Test
	void getOrCreateReturnsExistingPreference() {
		NotificationPreference existing = NotificationPreference.builder().id(1L).user(user).timezone("UTC")
				.dailyReminderEnabled(true).dailyReminderTime(LocalTime.of(20, 0)).build();
		when(preferenceRepository.findByUserId(user.getId())).thenReturn(Optional.of(existing));

		NotificationPreferenceResponse response = preferenceService.getOrCreate(user);

		assertEquals(LocalTime.of(20, 0), response.dailyReminderTime());
		verify(preferenceRepository, never()).save(any());
	}

	@Test
	void getOrCreateCreatesDefaultPreferenceWhenMissing() {
		when(preferenceRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
		when(preferenceRepository.save(any(NotificationPreference.class))).thenAnswer(returnsFirstArg());

		NotificationPreferenceResponse response = preferenceService.getOrCreate(user);

		assertEquals("UTC", response.timezone());
		assertFalse(response.dailyReminderEnabled());

		ArgumentCaptor<NotificationPreference> captor = ArgumentCaptor.forClass(NotificationPreference.class);
		verify(preferenceRepository).save(captor.capture());
		assertEquals(user, captor.getValue().getUser());
	}

	@Test
	void updatePersistsNewValues() {
		NotificationPreference existing = NotificationPreference.builder().id(1L).user(user)
				.timezone("America/Argentina/Buenos_Aires").dailyReminderEnabled(false).build();
		when(preferenceRepository.findByUserId(user.getId())).thenReturn(Optional.of(existing));
		when(preferenceRepository.save(any(NotificationPreference.class))).thenAnswer(returnsFirstArg());

		NotificationPreferenceResponse response = preferenceService.update(user,
				new NotificationPreferenceRequest(LocalTime.of(22, 30), "UTC", true));

		assertEquals(LocalTime.of(22, 30), response.dailyReminderTime());
		assertEquals("UTC", response.timezone());
		assertTrue(response.dailyReminderEnabled());
	}

	@Test
	void updateRejectsInvalidTimezone() {
		assertThrows(InvalidInputException.class, () -> preferenceService.update(user,
				new NotificationPreferenceRequest(LocalTime.of(20, 0), "Invalid/Zone", true)));
		verify(preferenceRepository, never()).save(any());
	}

	@Test
	void updateRejectsEnabledReminderWithoutTime() {
		assertThrows(InvalidInputException.class,
				() -> preferenceService.update(user, new NotificationPreferenceRequest(null, "UTC", true)));
		verify(preferenceRepository, never()).save(any());
	}
}
