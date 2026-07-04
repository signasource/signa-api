package com.signasource.signa_api.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.signasource.signa_api.auth.dto.AuthResponse;
import com.signasource.signa_api.auth.dto.ChangePasswordRequest;
import com.signasource.signa_api.auth.dto.ForgotPasswordRequest;
import com.signasource.signa_api.auth.dto.LoginRequest;
import com.signasource.signa_api.auth.dto.RefreshTokenRequest;
import com.signasource.signa_api.auth.dto.RegisterRequest;
import com.signasource.signa_api.auth.dto.ResendVerificationEmailRequest;
import com.signasource.signa_api.auth.dto.ResetPasswordRequest;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.auth.entity.Token;
import com.signasource.signa_api.auth.entity.TokenType;
import com.signasource.signa_api.auth.repository.TokenRepository;
import com.signasource.signa_api.exceptions.InvalidCredentialsException;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.InvalidTokenException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
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

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private TokenRepository tokenRepository;

    @Mock private EmailService emailService;

    @InjectMocks private AuthService authService;

    private final User testUser =
            User.builder()
                    .email(EMAIL)
                    .name(NAME)
                    .passwordHash("hashed_password")
                    .role(Role.USER)
                    .enabled(true)
                    .build();

    private final CustomUserDetails userDetails = new CustomUserDetails(testUser);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", EXPIRATION);
        ReflectionTestUtils.setField(authService, "passwordResetTokenExpiration", EXPIRATION);
        ReflectionTestUtils.setField(authService, "emailVerificationTokenExpiration", EXPIRATION);
    }

    @Test
    void shouldRegisterUserSuccessfully() {
        RegisterRequest request = new RegisterRequest(EMAIL, PASSWORD, NAME);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed_password");
        when(tokenRepository.save(any(Token.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request);

        verify(userRepository).existsByEmail(EMAIL);
        verify(passwordEncoder).encode(PASSWORD);
        verify(userRepository).save(any(User.class));
        verify(tokenRepository).save(any());
        verify(emailService).sendVerificationEmail(eq(EMAIL), any(String.class));
    }

    @Test
    void shouldThrowWhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest(EMAIL, PASSWORD, NAME);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

        assertThrows(ResourceAlreadyInUseException.class, () -> authService.register(request));

        verify(userRepository).existsByEmail(EMAIL);
        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldLoginAndReturnTokens() {
        LoginRequest request = new LoginRequest(EMAIL, PASSWORD);
        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn(ACCESS_TOKEN);
        Token refresh = createRefreshToken(REFRESH_TOKEN, Instant.now().plusSeconds(3600));
        when(tokenRepository.save(any())).thenReturn(refresh);

        AuthResponse response = authService.login(request);

        assertEquals(ACCESS_TOKEN, response.accessToken());
        assertEquals(REFRESH_TOKEN, response.refreshToken());
        verify(authenticationManager).authenticate(any());
        verify(jwtService).generateToken(any(CustomUserDetails.class));
        verify(tokenRepository).save(any());
    }

    @Test
    void shouldCaptureEncodedPasswordOnRegister() {
        RegisterRequest request = new RegisterRequest(EMAIL, PASSWORD, NAME);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed_password");
        when(tokenRepository.save(any(Token.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("hashed_password", captor.getValue().getPasswordHash());
    }

    @Test
    void shouldRefreshTokenSuccessfully() {
        Token oldToken = createRefreshToken(OLD_REFRESH_TOKEN, Instant.now().plusSeconds(3600));
        Token newToken = createRefreshToken(REFRESH_TOKEN, Instant.now().plusSeconds(3600));
        when(tokenRepository.findByTokenAndType(OLD_REFRESH_TOKEN, TokenType.REFRESH))
                .thenReturn(Optional.of(oldToken));
        when(jwtService.generateToken(any(CustomUserDetails.class))).thenReturn(ACCESS_TOKEN);
        when(tokenRepository.save(any())).thenReturn(newToken);

        AuthResponse response =
                authService.refreshToken(new RefreshTokenRequest(OLD_REFRESH_TOKEN));

        assertEquals(ACCESS_TOKEN, response.accessToken());
        assertEquals(REFRESH_TOKEN, response.refreshToken());
        verify(tokenRepository).findByTokenAndType(OLD_REFRESH_TOKEN, TokenType.REFRESH);
        verify(tokenRepository).delete(oldToken);
        verify(jwtService).generateToken(any(CustomUserDetails.class));
        verify(tokenRepository).save(any());
    }

    @Test
    void shouldFailWhenRefreshTokenNotFound() {
        when(tokenRepository.findByTokenAndType(INVALID_REFRESH_TOKEN, TokenType.REFRESH))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidTokenException.class,
                () -> authService.refreshToken(new RefreshTokenRequest(INVALID_REFRESH_TOKEN)));

        verify(tokenRepository).findByTokenAndType(INVALID_REFRESH_TOKEN, TokenType.REFRESH);
    }

    @Test
    void shouldFailWhenRefreshTokenExpired() {
        Token expired = createRefreshToken(EXPIRED_REFRESH_TOKEN, Instant.now().minusSeconds(3600));
        when(tokenRepository.findByTokenAndType(EXPIRED_REFRESH_TOKEN, TokenType.REFRESH))
                .thenReturn(Optional.of(expired));

        assertThrows(
                InvalidTokenException.class,
                () -> authService.refreshToken(new RefreshTokenRequest(EXPIRED_REFRESH_TOKEN)));

        verify(tokenRepository).findByTokenAndType(EXPIRED_REFRESH_TOKEN, TokenType.REFRESH);
        verify(tokenRepository).delete(expired);
    }

    @Test
    void shouldFailLoginWhenAccountIsNotVerified() {
        User disabledUser =
                User.builder()
                        .email(EMAIL)
                        .name(NAME)
                        .passwordHash("hashed_password")
                        .role(Role.USER)
                        .enabled(false)
                        .build();
        CustomUserDetails disabledUserDetails = new CustomUserDetails(disabledUser);
        Authentication auth =
                new UsernamePasswordAuthenticationToken(
                        disabledUserDetails, null, disabledUserDetails.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(auth);

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(new LoginRequest(EMAIL, PASSWORD)));
        verify(authenticationManager).authenticate(any());
        verifyNoInteractions(jwtService);
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void shouldVerifyAccountSuccessfully() {
        String token = "verification-token";

        User disabledUser =
                User.builder()
                        .email(EMAIL)
                        .name(NAME)
                        .passwordHash("hashed_password")
                        .role(Role.USER)
                        .enabled(false)
                        .build();

        var verificationToken =
                Token.builder()
                        .token(token)
                        .user(disabledUser)
                        .expiryDate(Instant.now().plusSeconds(3600))
                        .build();

        when(tokenRepository.findByTokenAndType(token, TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(verificationToken));

        authService.verifyAccount(token);

        assertTrue(disabledUser.isEnabled());

        verify(tokenRepository).findByTokenAndType(token, TokenType.EMAIL_VERIFICATION);
        verify(userRepository).save(disabledUser);
        verify(tokenRepository).delete(verificationToken);
    }

    @Test
    void shouldFailWhenVerificationTokenDoesNotExist() {
        String token = "invalid-token";
        when(tokenRepository.findByTokenAndType(token, TokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> authService.verifyAccount(token));

        verify(tokenRepository).findByTokenAndType(token, TokenType.EMAIL_VERIFICATION);
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
        Token refreshToken = createRefreshToken(REFRESH_TOKEN, Instant.now().plusSeconds(3600));
        when(tokenRepository.save(any(Token.class))).thenReturn(refreshToken);

        AuthResponse response = authService.changePassword(request);

        assertNotNull(response);
        assertEquals(ACCESS_TOKEN, response.accessToken());
        assertEquals(REFRESH_TOKEN, response.refreshToken());
        assertEquals("new-hashed-password", testUser.getPasswordHash());
        verify(passwordEncoder).matches(CURRENT_PASSWORD, ENCODED_PASSWORD);
        verify(passwordEncoder).matches(NEW_PASSWORD, ENCODED_PASSWORD);
        verify(passwordEncoder).encode(NEW_PASSWORD);
        verify(userRepository).save(testUser);
        verify(tokenRepository).deleteByUserAndType(testUser, TokenType.REFRESH);
        verify(jwtService).generateToken(any(CustomUserDetails.class));
        verify(tokenRepository).save(any(Token.class));
    }

    @Test
    void shouldFailWhenCurrentPasswordIsIncorrect() {
        ChangePasswordRequest request = new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null));
        when(passwordEncoder.matches(CURRENT_PASSWORD, ENCODED_PASSWORD)).thenReturn(false);

        assertThrows(InvalidInputException.class, () -> authService.changePassword(request));

        verify(passwordEncoder).matches(CURRENT_PASSWORD, ENCODED_PASSWORD);
        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).deleteByUserAndType(any(), any());
    }

    @Test
    void shouldFailWhenNewPasswordMatchesCurrentPassword() {
        ChangePasswordRequest request = new ChangePasswordRequest(CURRENT_PASSWORD, NEW_PASSWORD);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(userDetails, null));
        when(passwordEncoder.matches(CURRENT_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);
        when(passwordEncoder.matches(NEW_PASSWORD, ENCODED_PASSWORD)).thenReturn(true);

        assertThrows(InvalidInputException.class, () -> authService.changePassword(request));

        verify(passwordEncoder).matches(CURRENT_PASSWORD, ENCODED_PASSWORD);
        verify(passwordEncoder).matches(NEW_PASSWORD, ENCODED_PASSWORD);
        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).deleteByUserAndType(any(), any());
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
        when(tokenRepository.save(any()))
                .thenReturn(createRefreshToken(REFRESH_TOKEN, Instant.now().plusSeconds(3600)));

        authService.changePassword(request);

        InOrder inOrder = inOrder(userRepository, tokenRepository, jwtService);
        inOrder.verify(userRepository).save(testUser);
        inOrder.verify(tokenRepository).deleteByUserAndType(testUser, TokenType.REFRESH);
        inOrder.verify(jwtService).generateToken(any(CustomUserDetails.class));
        inOrder.verify(tokenRepository).save(any(Token.class));
    }

    @Test
    void shouldGeneratePasswordResetToken() {
        ForgotPasswordRequest request = new ForgotPasswordRequest(EMAIL);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(testUser));
        when(tokenRepository.save(any(Token.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authService.forgotPassword(request);

        verify(userRepository).findByEmail(EMAIL);
        verify(tokenRepository).deleteByUserAndType(testUser, TokenType.PASSWORD_RESET);
        verify(tokenRepository).save(any());
        verify(emailService).sendPasswordResetEmail(eq(EMAIL), any(String.class));
    }

    @Test
    void shouldIgnoreForgotPasswordForUnknownEmail() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        authService.forgotPassword(new ForgotPasswordRequest(EMAIL));

        verify(userRepository).findByEmail(EMAIL);
        verifyNoInteractions(tokenRepository);
        verify(emailService, never()).sendPasswordResetEmail(any(), any());
    }

    @Test
    void shouldResetPasswordSuccessfully() {
        String token = "reset-token";
        Token resetToken =
                Token.builder()
                        .token(token)
                        .user(testUser)
                        .expiryDate(Instant.now().plusSeconds(3600))
                        .build();
        when(tokenRepository.findByTokenAndType(token, TokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(resetToken));
        when(passwordEncoder.encode(PASSWORD)).thenReturn("new-hashed-password");

        authService.resetPassword(new ResetPasswordRequest(PASSWORD), token);

        assertEquals("new-hashed-password", testUser.getPasswordHash());
        verify(userRepository).save(testUser);
        verify(tokenRepository).delete(resetToken);
        verify(tokenRepository).deleteByUserAndType(testUser, TokenType.REFRESH);
    }

    @Test
    void shouldFailWhenResetTokenDoesNotExist() {
        String token = "invalid-token";
        when(tokenRepository.findByTokenAndType(token, TokenType.PASSWORD_RESET))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidTokenException.class,
                () -> authService.resetPassword(new ResetPasswordRequest(PASSWORD), token));

        verify(tokenRepository).findByTokenAndType(token, TokenType.PASSWORD_RESET);
        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldResendVerificationEmail() {
        ResendVerificationEmailRequest request = new ResendVerificationEmailRequest(EMAIL);
        testUser.setEnabled(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(testUser));
        when(tokenRepository.save(any(Token.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        authService.resendVerificationEmail(request);

        verify(userRepository).findByEmail(EMAIL);
        verify(tokenRepository).deleteByUserAndType(testUser, TokenType.EMAIL_VERIFICATION);
        verify(tokenRepository).save(any(Token.class));
        verify(emailService).sendVerificationEmail(eq(EMAIL), any(String.class));
    }

    @Test
    void shouldDoNothingWhenUserAlreadyVerified() {
        ResendVerificationEmailRequest request = new ResendVerificationEmailRequest(EMAIL);
        testUser.setEnabled(true);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(testUser));

        authService.resendVerificationEmail(request);

        verify(userRepository).findByEmail(EMAIL);
        verify(tokenRepository, never()).deleteByUserAndType(any(), any());
        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(any(), any());
    }

    private Token createRefreshToken(String token, Instant expiryDate) {
        return Token.builder()
                .token(token)
                .user(testUser)
                .expiryDate(expiryDate)
                .type(TokenType.REFRESH)
                .build();
    }
}
