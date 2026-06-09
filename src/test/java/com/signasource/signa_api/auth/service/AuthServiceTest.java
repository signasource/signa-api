package com.signasource.signa_api.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.signasource.signa_api.auth.dto.AuthResponse;
import com.signasource.signa_api.auth.dto.ChangePasswordRequest;
import com.signasource.signa_api.auth.dto.ForgotPasswordRequest;
import com.signasource.signa_api.auth.dto.LoginRequest;
import com.signasource.signa_api.auth.dto.RefreshTokenRequest;
import com.signasource.signa_api.auth.dto.RegisterRequest;
import com.signasource.signa_api.auth.dto.ResetPasswordRequest;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.auth.entity.EmailVerificationToken;
import com.signasource.signa_api.auth.entity.PasswordResetToken;
import com.signasource.signa_api.auth.entity.RefreshToken;
import com.signasource.signa_api.auth.repository.EmailVerificationTokenRepository;
import com.signasource.signa_api.auth.repository.PasswordResetTokenRepository;
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
	private static final Long EXPIRATION = 3600000L;
	private static final String ACCESS_TOKEN = "access-token-123";
	private static final String REFRESH_TOKEN = "refresh-token-123";
	private static final String OLD_REFRESH_TOKEN = "old-refresh-token";
	private static final String INVALID_REFRESH_TOKEN = "invalid-refresh-token";
	private static final String EXPIRED_REFRESH_TOKEN = "expired-refresh-token";
	private static final String CURRENT_PASSWORD = "current-password";
	private static final String NEW_PASSWORD = "new-password";
	private static final String ENCODED_PASSWORD = "hashed_password";

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
	@Mock
	private EmailVerificationTokenRepository emailVerificationTokenRepository;
	@Mock
	private EmailService emailService;
	@Mock
	private PasswordResetTokenRepository passwordResetTokenRepository;

	@InjectMocks
	private AuthService authService;

	private final User testUser = User.builder().email(EMAIL).name(NAME).passwordHash("hashed_password").role(Role.USER)
			.enabled(true).build();

	private final CustomUserDetails userDetails = new CustomUserDetails(testUser);

	@BeforeEach
	void setUp() {
		ReflectionTestUtils.setField(authService, "refreshTokenExpiration", EXPIRATION);
	}

	@Test
	void shouldRegisterUserSuccessfully() {
		RegisterRequest request = new RegisterRequest(EMAIL, PASSWORD, NAME);
		when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
		when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed_password");
		when(emailVerificationTokenRepository.save(any())).thenReturn(null);

		authService.register(request);

		verify(userRepository).existsByEmail(EMAIL);
		verify(passwordEncoder).encode(PASSWORD);
		verify(userRepository).save(any(User.class));
		verify(emailVerificationTokenRepository).save(any());
		verify(emailService).sendVerificationEmail(eq(EMAIL), any(String.class));
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
		when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn(ACCESS_TOKEN);
		RefreshToken refresh = createRefreshToken(REFRESH_TOKEN, Instant.now().plusSeconds(3600));
		when(refreshTokenRepository.save(any())).thenReturn(refresh);

		AuthResponse response = authService.login(request);

		assertEquals(ACCESS_TOKEN, response.accessToken());
		assertEquals(REFRESH_TOKEN, response.refreshToken());
		verify(authenticationManager).authenticate(any());
		verify(jwtService).generateToken(any(CustomUserDetails.class));
		verify(refreshTokenRepository).save(any());
	}

	@Test
	void shouldCaptureEncodedPasswordOnRegister() {
		RegisterRequest request = new RegisterRequest(EMAIL, PASSWORD, NAME);
		when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
		when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed_password");
		when(emailVerificationTokenRepository.save(any())).thenReturn(null);

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

	@Test
	void shouldFailLoginWhenAccountIsNotVerified() {
		User disabledUser = User.builder().email(EMAIL).name(NAME).passwordHash("hashed_password").role(Role.USER)
				.enabled(false).build();
		CustomUserDetails disabledUserDetails = new CustomUserDetails(disabledUser);
		Authentication auth = new UsernamePasswordAuthenticationToken(disabledUserDetails, null,
				disabledUserDetails.getAuthorities());
		when(authenticationManager.authenticate(any())).thenReturn(auth);

		assertThrows(InvalidCredentialsException.class, () -> authService.login(new LoginRequest(EMAIL, PASSWORD)));
		verify(authenticationManager).authenticate(any());
		verifyNoInteractions(jwtService);
		verify(refreshTokenRepository, never()).save(any());
	}

	@Test
	void shouldVerifyAccountSuccessfully() {
		String token = "verification-token";

		User disabledUser = User.builder().email(EMAIL).name(NAME).passwordHash("hashed_password").role(Role.USER)
				.enabled(false).build();

		var verificationToken = EmailVerificationToken.builder().token(token).user(disabledUser)
				.expiryDate(Instant.now().plusSeconds(3600)).build();

		when(emailVerificationTokenRepository.findByToken(token)).thenReturn(Optional.of(verificationToken));

		authService.verifyAccount(token);

		assertTrue(disabledUser.isEnabled());

		verify(emailVerificationTokenRepository).findByToken(token);
		verify(userRepository).save(disabledUser);
		verify(emailVerificationTokenRepository).delete(verificationToken);
	}

	@Test
	void shouldFailWhenVerificationTokenDoesNotExist() {
		String token = "invalid-token";
		when(emailVerificationTokenRepository.findByToken(token)).thenReturn(Optional.empty());

		assertThrows(InvalidCredentialsException.class, () -> authService.verifyAccount(token));

		verify(emailVerificationTokenRepository).findByToken(token);
		verifyNoInteractions(userRepository);
	}

	@Test
	void shouldChangePasswordSuccessfully() {
		ChangePasswordRequest request = new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD);
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null));
		when(passwordEncoder.matches(CURRENT_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
		when(passwordEncoder.matches(NEW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);
		when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("new-hashed-password");
		when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn(ACCESS_TOKEN);
		RefreshToken refreshToken = createRefreshToken(REFRESH_TOKEN, Instant.now().plusSeconds(3600));
		when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(refreshToken);

		AuthResponse response = authService.changePassword(request);

		assertNotNull(response);
		assertEquals(ACCESS_TOKEN, response.accessToken());
		assertEquals(REFRESH_TOKEN, response.refreshToken());
		assertEquals("new-hashed-password", testUser.getPasswordHash());
		verify(passwordEncoder).matches(CURRENT_PASSWORD, ENCODED_PASSWORD);
		verify(passwordEncoder).matches(NEW_PASSWORD, ENCODED_PASSWORD);
		verify(passwordEncoder).encode(NEW_PASSWORD);
		verify(userRepository).save(testUser);
		verify(refreshTokenRepository).deleteByUser(testUser);
		verify(jwtService).generateToken(any(CustomUserDetails.class));
		verify(refreshTokenRepository).save(any(RefreshToken.class));
	}

	@Test
	void shouldFailWhenCurrentPasswordIsIncorrect() {
		ChangePasswordRequest request = new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD);
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null));
		when(passwordEncoder.matches(CURRENT_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

		assertThrows(InvalidCredentialsException.class, () -> authService.changePassword(request));

		verify(passwordEncoder).matches(CURRENT_PASSWORD, ENCODED_PASSWORD);
		verify(userRepository, never()).save(any());
		verify(refreshTokenRepository, never()).deleteByUser(any());
	}

	@Test
	void shouldFailWhenNewPasswordMatchesCurrentPassword() {
		ChangePasswordRequest request = new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD);
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null));
		when(passwordEncoder.matches(CURRENT_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
		when(passwordEncoder.matches(NEW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

		assertThrows(InvalidCredentialsException.class, () -> authService.changePassword(request));

		verify(passwordEncoder).matches(CURRENT_PASSWORD, ENCODED_PASSWORD);
		verify(passwordEncoder).matches(NEW_PASSWORD, ENCODED_PASSWORD);
		verify(userRepository, never()).save(any());
		verify(refreshTokenRepository, never()).deleteByUser(any());
	}

	@Test
	void shouldInvalidateAllRefreshTokensBeforeGeneratingNewOnes() {
		ChangePasswordRequest request = new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD);
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null));
		when(passwordEncoder.matches(CURRENT_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
		when(passwordEncoder.matches(NEW_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);
		when(passwordEncoder.encode(NEW_PASSWORD)).thenReturn("new-hashed-password");
		when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn(ACCESS_TOKEN);
		when(refreshTokenRepository.save(any()))
				.thenReturn(createRefreshToken(REFRESH_TOKEN, Instant.now().plusSeconds(3600)));

		authService.changePassword(request);

		InOrder inOrder = inOrder(userRepository, refreshTokenRepository, jwtService);
		inOrder.verify(userRepository).save(testUser);
		inOrder.verify(refreshTokenRepository).deleteByUser(testUser);
		inOrder.verify(jwtService).generateToken(any(CustomUserDetails.class));
		inOrder.verify(refreshTokenRepository).save(any(RefreshToken.class));
	}

	@Test
	void shouldGeneratePasswordResetToken() {
		ForgotPasswordRequest request = new ForgotPasswordRequest(EMAIL);
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(testUser));

		authService.forgotPassword(request);

		verify(userRepository).findByEmail(EMAIL);
		verify(passwordResetTokenRepository).deleteByUser(testUser);
		verify(passwordResetTokenRepository).save(any());
		verify(emailService).sendPasswordResetEmail(eq(EMAIL), any(String.class));
	}

	@Test
	void shouldIgnoreForgotPasswordForUnknownEmail() {
		when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

		authService.forgotPassword(new ForgotPasswordRequest(EMAIL));

		verify(userRepository).findByEmail(EMAIL);
		verifyNoInteractions(passwordResetTokenRepository);
		verify(emailService, never()).sendPasswordResetEmail(any(), any());
	}

	@Test
	void shouldResetPasswordSuccessfully() {
		String token = "reset-token";
		PasswordResetToken resetToken = PasswordResetToken.builder().token(token).user(testUser)
				.expiryDate(Instant.now().plusSeconds(3600)).build();
		when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.of(resetToken));
		when(passwordEncoder.encode(PASSWORD)).thenReturn("new-hashed-password");

		authService.resetPassword(new ResetPasswordRequest(PASSWORD), token);

		assertEquals("new-hashed-password", testUser.getPasswordHash());
		verify(userRepository).save(testUser);
		verify(passwordResetTokenRepository).delete(resetToken);
		verify(refreshTokenRepository).deleteByUser(testUser);
	}

	@Test
	void shouldFailWhenResetTokenDoesNotExist() {
		String token = "invalid-token";
		when(passwordResetTokenRepository.findByToken(token)).thenReturn(Optional.empty());

		assertThrows(InvalidCredentialsException.class,
				() -> authService.resetPassword(new ResetPasswordRequest(PASSWORD), token));

		verify(passwordResetTokenRepository).findByToken(token);
		verifyNoInteractions(userRepository);
	}

	private RefreshToken createRefreshToken(String token, Instant expiryDate) {
		return RefreshToken.builder().token(token).user(testUser).expiryDate(expiryDate).build();
	}
}
