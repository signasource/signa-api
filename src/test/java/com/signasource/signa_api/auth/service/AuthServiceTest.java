package com.signasource.signa_api.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.signasource.signa_api.auth.dto.AuthResponse;
import com.signasource.signa_api.auth.dto.LoginRequest;
import com.signasource.signa_api.auth.dto.RefreshTokenRequest;
import com.signasource.signa_api.auth.dto.RegisterRequest;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.auth.entity.RefreshToken;
import com.signasource.signa_api.auth.repository.RefreshTokenRepository;
import com.signasource.signa_api.exceptions.InvalidCredentialsException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUse;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	private static final String EMAIL = "test@example.com";
	private static final String PASSWORD = "password123";
	private static final String NAME = "Test User";

	private static final String ACCESS_TOKEN = "access-token-123";
	private static final String REFRESH_TOKEN = "refresh-token-123";
	private static final String OLD_REFRESH_TOKEN = "old-refresh-token";
	private static final String INVALID_REFRESH_TOKEN = "invalid-refresh-token";
	private static final String EXPIRED_REFRESH_TOKEN = "expired-refresh-token";

	@Mock
	private UserRepository userRepository;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private AuthenticationManager authenticationManager;
	@Mock
	private JwtService jwtService;
	@Mock
	private RefreshTokenRepository refreshTokenRepository;

	@InjectMocks
	private AuthService authService;

	private final User testUser = User.builder().email(EMAIL).name(NAME).passwordHash("hashed_password").role(Role.USER)
			.build();

	private final CustomUserDetails userDetails = new CustomUserDetails(testUser);

	@Test
	void shouldRegisterUserSuccessfully() {
		RegisterRequest request = new RegisterRequest(EMAIL, PASSWORD, NAME);
		when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
		when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed_password");

		authService.register(request);

		verify(userRepository).existsByEmail(EMAIL);
		verify(passwordEncoder).encode(PASSWORD);
		verify(userRepository).save(any(User.class));
	}

	@Test
	void shouldThrowWhenEmailAlreadyExists() {
		RegisterRequest request = new RegisterRequest(EMAIL, PASSWORD, NAME);
		when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

		assertThrows(ResourceAlreadyInUse.class, () -> authService.register(request));

		verify(userRepository).existsByEmail(EMAIL);
		verifyNoInteractions(passwordEncoder);
		verify(userRepository, never()).save(any());
	}

	@Test
	void shouldLoginAndReturnTokens() {
		LoginRequest request = new LoginRequest(EMAIL, PASSWORD);
		Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
		when(authenticationManager.authenticate(any())).thenReturn(auth);
		when(jwtService.generateToken(userDetails)).thenReturn(ACCESS_TOKEN);
		RefreshToken refresh = createRefreshToken(REFRESH_TOKEN, Instant.now().plusSeconds(3600));
		when(refreshTokenRepository.save(any())).thenReturn(refresh);

		AuthResponse response = authService.login(request);

		assertEquals(ACCESS_TOKEN, response.accessToken());
		assertEquals(REFRESH_TOKEN, response.refreshToken());
		verify(authenticationManager).authenticate(any());
		verify(jwtService).generateToken(userDetails);
		verify(refreshTokenRepository).save(any());
	}

	@Test
	void shouldCaptureEncodedPasswordOnRegister() {
		RegisterRequest request = new RegisterRequest(EMAIL, PASSWORD, NAME);
		when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
		when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed_password");

		authService.register(request);

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		assertEquals("hashed_password", captor.getValue().getPasswordHash());
	}

	@Test
	void shouldRefreshTokenSuccessfully() {
		RefreshToken oldToken = createRefreshToken(OLD_REFRESH_TOKEN, Instant.now().plusSeconds(3600));
		RefreshToken newToken = createRefreshToken(REFRESH_TOKEN, Instant.now().plusSeconds(3600));
		when(refreshTokenRepository.findByToken(OLD_REFRESH_TOKEN)).thenReturn(Optional.of(oldToken));
		when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn(ACCESS_TOKEN);
		when(refreshTokenRepository.save(any())).thenReturn(newToken);

		AuthResponse response = authService.refreshToken(new RefreshTokenRequest(OLD_REFRESH_TOKEN));

		assertEquals(ACCESS_TOKEN, response.accessToken());
		assertEquals(REFRESH_TOKEN, response.refreshToken());
		verify(refreshTokenRepository).findByToken(OLD_REFRESH_TOKEN);
		verify(refreshTokenRepository).delete(oldToken);
		verify(jwtService).generateToken(any(CustomUserDetails.class));
		verify(refreshTokenRepository).save(any());
	}

	@Test
	void shouldFailWhenRefreshTokenNotFound() {
		when(refreshTokenRepository.findByToken(INVALID_REFRESH_TOKEN)).thenReturn(Optional.empty());

		assertThrows(InvalidCredentialsException.class,
				() -> authService.refreshToken(new RefreshTokenRequest(INVALID_REFRESH_TOKEN)));

		verify(refreshTokenRepository).findByToken(INVALID_REFRESH_TOKEN);
	}

	@Test
	void shouldFailWhenRefreshTokenExpired() {
		RefreshToken expired = createRefreshToken(EXPIRED_REFRESH_TOKEN, Instant.now().minusSeconds(3600));
		when(refreshTokenRepository.findByToken(EXPIRED_REFRESH_TOKEN)).thenReturn(Optional.of(expired));

		assertThrows(InvalidCredentialsException.class,
				() -> authService.refreshToken(new RefreshTokenRequest(EXPIRED_REFRESH_TOKEN)));

		verify(refreshTokenRepository).findByToken(EXPIRED_REFRESH_TOKEN);
		verify(refreshTokenRepository).delete(expired);
	}

	private RefreshToken createRefreshToken(String token, Instant expiryDate) {
		return RefreshToken.builder().token(token).user(testUser).expiryDate(expiryDate).build();
	}
}
