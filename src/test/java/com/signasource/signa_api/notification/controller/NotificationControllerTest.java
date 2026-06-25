package com.signasource.signa_api.notification.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.notification.dto.NotificationPreferenceRequest;
import com.signasource.signa_api.notification.dto.NotificationPreferenceResponse;
import com.signasource.signa_api.notification.dto.NotificationResponse;
import com.signasource.signa_api.notification.dto.UnreadCountResponse;
import com.signasource.signa_api.notification.entity.NotificationCode;
import com.signasource.signa_api.notification.service.NotificationPreferenceService;
import com.signasource.signa_api.notification.service.NotificationService;
import com.signasource.signa_api.users.entity.User;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

	@Mock
	private NotificationService notificationService;
	@Mock
	private NotificationPreferenceService preferenceService;

	@InjectMocks
	private NotificationController controller;

	private final User user = User.builder().id(UUID.randomUUID()).email("a@b.com").build();
	private final CustomUserDetails userDetails = new CustomUserDetails(user);
	private final NotificationResponse sample = new NotificationResponse(1L, NotificationCode.DAILY_REMINDER, "t", "b",
			false, Instant.now(), null, Map.of());

	@Test
	void getInbox() {
		Pageable pageable = PageRequest.of(0, 20);
		when(notificationService.getInbox(user, pageable)).thenReturn(new PageImpl<>(List.of(sample)));

		ResponseEntity<?> response = controller.getInbox(userDetails, pageable);

		assertEquals(HttpStatus.OK, response.getStatusCode());
	}

	@Test
	void getUnread() {
		when(notificationService.getUnread(user)).thenReturn(List.of(sample));

		ResponseEntity<List<NotificationResponse>> response = controller.getUnread(userDetails);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(1, response.getBody().size());
	}

	@Test
	void getUnreadCount() {
		when(notificationService.countUnread(user)).thenReturn(4L);

		ResponseEntity<UnreadCountResponse> response = controller.getUnreadCount(userDetails);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(4L, response.getBody().count());
	}

	@Test
	void markAsRead() {
		ResponseEntity<Void> response = controller.markAsRead(userDetails, 7L);

		verify(notificationService).markAsRead(user, 7L);
		assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
	}

	@Test
	void markAllAsRead() {
		ResponseEntity<Void> response = controller.markAllAsRead(userDetails);

		verify(notificationService).markAllAsRead(user);
		assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
	}

	@Test
	void getPreferences() {
		NotificationPreferenceResponse prefResponse = new NotificationPreferenceResponse(LocalTime.of(20, 0), "UTC",
				true);
		when(preferenceService.getOrCreate(user)).thenReturn(prefResponse);

		ResponseEntity<NotificationPreferenceResponse> response = controller.getPreferences(userDetails);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(prefResponse, response.getBody());
	}

	@Test
	void updatePreferences() {
		NotificationPreferenceRequest request = new NotificationPreferenceRequest(LocalTime.of(20, 0), "UTC", true);
		NotificationPreferenceResponse prefResponse = new NotificationPreferenceResponse(LocalTime.of(20, 0), "UTC",
				true);
		when(preferenceService.update(user, request)).thenReturn(prefResponse);

		ResponseEntity<NotificationPreferenceResponse> response = controller.updatePreferences(userDetails, request);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(prefResponse, response.getBody());
	}
}
