package com.signasource.signa_api.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

	private static final String EMAIL = "test@example.com";
	private static final String DIFFERENT_EMAIL = "different@example.com";
	private static final String SECRET = "my-test-secret-key-with-sufficient-length-for-hmac-256-algorithm-123456";
	private static final Long EXPIRATION = 3600000L;

	@InjectMocks
	private JwtService jwtService;

	private UserDetails userDetails;

	@BeforeEach
	void setUp() {
		User user = User.builder().email(EMAIL).name("Test User").passwordHash("hashed_password_123").role(Role.USER)
				.build();

		userDetails = new CustomUserDetails(user);

		ReflectionTestUtils.setField(jwtService, "secret", SECRET);
		ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", EXPIRATION);
	}

	@Test
	void shouldGenerateValidToken() {
		String token = jwtService.generateToken(userDetails);

		assertNotNull(token);
		assertFalse(token.isEmpty());
		assertTrue(token.contains("."));
	}

	@Test
	void shouldExtractUsernameFromToken() {
		String token = jwtService.generateToken(userDetails);

		String username = jwtService.extractUsername(token);

		assertEquals(EMAIL, username);
	}

	@Test
	void shouldValidateTokenWithCorrectUser() {
		String token = jwtService.generateToken(userDetails);

		boolean isValid = jwtService.isValid(token, userDetails);

		assertTrue(isValid);
	}

	@Test
	void shouldRejectTokenWithDifferentUser() {
		String token = jwtService.generateToken(userDetails);
		User differentUser = User.builder().email(DIFFERENT_EMAIL).name("Different User")
				.passwordHash("hashed_password_456").role(Role.USER).build();
		UserDetails differentDetails = new CustomUserDetails(differentUser);

		boolean isValid = jwtService.isValid(token, differentDetails);

		assertFalse(isValid);
	}

	@Test
	void shouldGenerateTokenWithCorrectStructure() {
		String token = jwtService.generateToken(userDetails);

		String[] parts = token.split("\\.");
		assertEquals(3, parts.length);
	}
}
