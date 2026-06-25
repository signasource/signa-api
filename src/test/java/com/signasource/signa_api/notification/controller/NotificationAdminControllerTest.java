package com.signasource.signa_api.notification.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.signasource.signa_api.notification.dto.BroadcastRequest;
import com.signasource.signa_api.notification.entity.NotificationCode;
import com.signasource.signa_api.notification.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class NotificationAdminControllerTest {

	@Mock
	private NotificationService notificationService;

	@InjectMocks
	private NotificationAdminController controller;

	@Test
	void broadcast() {
		BroadcastRequest request = new BroadcastRequest(NotificationCode.GLOBAL_ANNOUNCEMENT, "title", "body");

		ResponseEntity<Void> response = controller.broadcast(request);

		verify(notificationService).broadcastGlobal(NotificationCode.GLOBAL_ANNOUNCEMENT, "title", "body");
		assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
	}
}
