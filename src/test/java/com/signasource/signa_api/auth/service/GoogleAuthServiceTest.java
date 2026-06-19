package com.signasource.signa_api.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.signasource.signa_api.auth.dto.AuthResponse;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.auth.entity.Token;
import com.signasource.signa_api.auth.repository.TokenRepository;
import com.signasource.signa_api.exceptions.InvalidCredentialsException;
import com.signasource.signa_api.users.entity.AuthProvider;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class GoogleAuthServiceTest {

	@Mock
	private UserRepository userRepository;
	@Mock
	private JwtService jwtService;
	@Mock
	private TokenRepository tokenRepository;
	@Mock
	private GoogleIdTokenVerifier verifier;

	@InjectMocks
	private GoogleAuthService googleAuthService;

	@Mock
	private GoogleIdToken mockedGoogleToken;

	private final String VALID_TOKEN_STRING = "valid.google.token";
	private final String EMAIL = "test@gmail.com";
	private final String NAME = "Test User";

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(googleAuthService, "refreshTokenExpiration", 604800000L);
	}

	@Test
	void shouldRegisterNewUserAndReturnTokens_whenTokenIsValidAndUserDoesNotExist() throws Exception {
		GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
		payload.setEmail(EMAIL);
		payload.set("name", NAME);

		when(verifier.verify(VALID_TOKEN_STRING)).thenReturn(mockedGoogleToken);
		when(mockedGoogleToken.getPayload()).thenReturn(payload);
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
		when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn("access-token-123");

		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(tokenRepository.save(any(Token.class))).thenAnswer(invocation -> invocation.getArgument(0));

		AuthResponse response = googleAuthService.authenticateWithGoogle(VALID_TOKEN_STRING);

		assertNotNull(response);
		assertEquals("access-token-123", response.accessToken());
		assertNotNull(response.refreshToken());

		ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());

		User savedUser = userCaptor.getValue();
		assertEquals(EMAIL, savedUser.getEmail());
		assertEquals(NAME, savedUser.getName());
		assertEquals(AuthProvider.GOOGLE, savedUser.getProvider());
		assertNull(savedUser.getPasswordHash());
		assertTrue(savedUser.isEnabled());
	}

	@Test
	void shouldReturnTokens_whenTokenIsValidAndUserAlreadyExists() throws Exception {
		GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
		payload.setEmail(EMAIL);

		User existingUser = User.builder().id(UUID.randomUUID()).email(EMAIL).provider(AuthProvider.LOCAL).build();

		when(verifier.verify(VALID_TOKEN_STRING)).thenReturn(mockedGoogleToken);
		when(mockedGoogleToken.getPayload()).thenReturn(payload);
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser));
		when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn("access-token-123");

		AuthResponse response = googleAuthService.authenticateWithGoogle(VALID_TOKEN_STRING);

		assertNotNull(response);
		assertEquals("access-token-123", response.accessToken());

		verify(userRepository, never()).save(any(User.class));
		verify(tokenRepository).save(any(Token.class));
	}

	@Test
	void shouldThrowException_whenGoogleTokenIsInvalid() throws Exception {
		String invalidToken = "invalid.token";
		when(verifier.verify(invalidToken)).thenReturn(null);

		InvalidCredentialsException exception = assertThrows(InvalidCredentialsException.class,
				() -> googleAuthService.authenticateWithGoogle(invalidToken));

		assertTrue(exception.getMessage().contains("Token de Google inválido"));

		verifyNoInteractions(userRepository);
		verifyNoInteractions(jwtService);
		verifyNoInteractions(tokenRepository);
	}
}
