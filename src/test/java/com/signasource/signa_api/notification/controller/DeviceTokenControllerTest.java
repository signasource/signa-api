package com.signasource.signa_api.notification.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.notification.dto.DeviceTokenRequest;
import com.signasource.signa_api.notification.entity.DevicePlatform;
import com.signasource.signa_api.notification.service.DeviceTokenService;
import com.signasource.signa_api.users.entity.User;

@ExtendWith(MockitoExtension.class)
class DeviceTokenControllerTest {

	@Mock
	private DeviceTokenService deviceTokenService;

	@InjectMocks
	private DeviceTokenController controller;

	private final User user = User.builder().id(UUID.randomUUID()).email("a@b.com").build();
	private final CustomUserDetails userDetails = new CustomUserDetails(user);

	@Test
	void registerDeviceToken() {
		DeviceTokenRequest request = new DeviceTokenRequest("token", DevicePlatform.ANDROID);

		ResponseEntity<Void> response = controller.registerDeviceToken(userDetails, request);

		verify(deviceTokenService).registerToken(user, "token", DevicePlatform.ANDROID);
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
	}

	@Test
	void removeDeviceToken() {
		DeviceTokenRequest request = new DeviceTokenRequest("token", null);

		ResponseEntity<Void> response = controller.removeDeviceToken(userDetails, request);

		verify(deviceTokenService).removeToken(user, "token");
		assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
	}

	@Test
	void removeAllDeviceTokens() {
		ResponseEntity<Void> response = controller.removeAllDeviceTokens(userDetails);

		verify(deviceTokenService).removeAllTokensForUser(user);
		assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
	}
}
