package com.signasource.signa_api.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.signasource.signa_api.auth.dto.AuthResponse;
import com.signasource.signa_api.auth.dto.ForgotPasswordRequest;
import com.signasource.signa_api.auth.dto.LoginRequest;
import com.signasource.signa_api.auth.dto.RefreshTokenRequest;
import com.signasource.signa_api.auth.dto.RegisterRequest;
import com.signasource.signa_api.auth.dto.GoogleAuthRequest;
import com.signasource.signa_api.auth.dto.ResendVerificationEmailRequest;
import com.signasource.signa_api.auth.dto.ResetPasswordRequest;
import com.signasource.signa_api.auth.service.AuthService;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;

    @InjectMocks private AuthController authController;

    private RegisterRequest registerRequest =
            new RegisterRequest("test@example.com", "password123", "Test User");
    private LoginRequest loginRequest = new LoginRequest("test@example.com", "password123");
	private GoogleAuthRequest googleAuthRequest = new GoogleAuthRequest("valid.google.id.token");

    @Test
    void testRegister() {
        doNothing().when(authService).register(any(RegisterRequest.class));

        ResponseEntity<Void> response = authController.register(registerRequest);

        verify(authService).register(any(RegisterRequest.class));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void testLogin() {
        AuthResponse authResponse = new AuthResponse("test-jwt-token", "test-refresh-token");
        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        ResponseEntity<AuthResponse> response = authController.login(loginRequest);

        verify(authService).login(any(LoginRequest.class));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(authResponse.accessToken(), response.getBody().accessToken());
        assertEquals(authResponse.refreshToken(), response.getBody().refreshToken());
    }

    @Test
    void testRefresh() {
        RefreshTokenRequest refreshTokenRequest = new RefreshTokenRequest("valid-refresh-token");
        AuthResponse authResponse = new AuthResponse("new-access-token", "new-refresh-token");
        when(authService.refreshToken(any(RefreshTokenRequest.class))).thenReturn(authResponse);

        ResponseEntity<AuthResponse> response = authController.refresh(refreshTokenRequest);

        verify(authService).refreshToken(any(RefreshTokenRequest.class));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(authResponse.accessToken(), response.getBody().accessToken());
        assertEquals(authResponse.refreshToken(), response.getBody().refreshToken());
    }

    @Test
    void testForgotPassword() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("test@example.com");
        doNothing().when(authService).forgotPassword(any(ForgotPasswordRequest.class));

        ResponseEntity<Void> response = authController.forgotPassword(request);

        verify(authService).forgotPassword(any(ForgotPasswordRequest.class));
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testResetPassword() {
        String token = "reset-token";
        ResetPasswordRequest request = new ResetPasswordRequest("new-password");
        doNothing().when(authService).resetPassword(any(ResetPasswordRequest.class), eq(token));

        ResponseEntity<Void> response = authController.resetPassword(request, token);

        verify(authService).resetPassword(any(ResetPasswordRequest.class), eq(token));
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void testResendVerificationEmail() {
        ResendVerificationEmailRequest request =
                new ResendVerificationEmailRequest(registerRequest.email());
        doNothing()
                .when(authService)
                .resendVerificationEmail(any(ResendVerificationEmailRequest.class));

        ResponseEntity<Void> response = authController.resendVerificationEmail(request);

        verify(authService).resendVerificationEmail(any(ResendVerificationEmailRequest.class));
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

	@Test
	void testAuthenticateWithGoogle() {
		AuthResponse expectedResponse = new AuthResponse("google-access-token", "google-refresh-token");
		when(authService.authenticateWithGoogle(googleAuthRequest.idToken())).thenReturn(expectedResponse);

		ResponseEntity<AuthResponse> response = authController.authenticateWithGoogle(googleAuthRequest);

		verify(authService).authenticateWithGoogle(googleAuthRequest.idToken());
		assertEquals(HttpStatus.OK, response.getStatusCode());
		Assertions.assertNotNull(response.getBody());
		assertEquals(expectedResponse.accessToken(), response.getBody().accessToken());
	}
}
