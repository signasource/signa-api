package com.signasource.signa_api.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.signasource.signa_api.auth.dto.LoginRequest;
import com.signasource.signa_api.auth.dto.LoginResponse;
import com.signasource.signa_api.auth.dto.RegisterRequest;
import com.signasource.signa_api.auth.service.AuthService;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

	@Mock
	private AuthService authService;

	@InjectMocks
	private AuthController authController;

	private RegisterRequest registerRequest = new RegisterRequest("test@example.com", "password123", "Test User");
	private LoginRequest loginRequest = new LoginRequest("test@example.com", "password123");

	@Test
	void testRegister() {
		doNothing().when(authService).register(any(RegisterRequest.class));

		ResponseEntity<Void> response = authController.register(registerRequest);

		verify(authService).register(any(RegisterRequest.class));
		assertEquals(HttpStatus.CREATED, response.getStatusCode());
	}

	@Test
	void testLogin() {
		String expectedToken = "test-jwt-token";
		when(authService.login(any(LoginRequest.class))).thenReturn(expectedToken);

		ResponseEntity<LoginResponse> response = authController.login(loginRequest);

		verify(authService).login(any(LoginRequest.class));
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertEquals(expectedToken, response.getBody().token());
	}
}
