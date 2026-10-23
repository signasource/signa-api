package com.signasource.signa_api.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
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
import com.signasource.signa_api.common.MetricsService;
import com.signasource.signa_api.exceptions.InvalidCredentialsException;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.InvalidResetCodeException;
import com.signasource.signa_api.exceptions.InvalidTokenException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.users.entity.AuthProvider;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserSettings;
import com.signasource.signa_api.users.repository.UserRepository;
import com.signasource.signa_api.users.repository.UserSettingsRepository;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Failed attempts after which a password reset code is discarded. */
    static final int MAX_RESET_CODE_ATTEMPTS = 5;

    private final SecureRandom secureRandom = new SecureRandom();

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserStatsRepository userStatsRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TokenRepository tokenRepository;
    private final EmailService emailService;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final MetricsService metricsService;

    @Value("${auth.token-expirations.refresh}")
    private Long refreshTokenExpiration;

    @Value("${auth.token-expirations.password-reset}")
    private Long passwordResetTokenExpiration;

    @Value("${auth.token-expirations.email-verification}")
    private Long emailVerificationTokenExpiration;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResourceAlreadyInUseException("Email already in use");
        }

        if (userRepository.existsByUsername(request.username())) {
            throw new ResourceAlreadyInUseException("Username already in use");
        }

        User user =
                User.builder()
                        .email(request.email())
                        .username(request.username())
                        .name(request.name())
                        .lastName(request.lastName())
                        .passwordHash(passwordEncoder.encode(request.password()))
                        .role(Role.USER)
                        .enabled(true)
                        .verified(false)
                        .providers(new HashSet<>(Set.of(AuthProvider.LOCAL)))
                        .build();

        userRepository.save(user);

        userSettingsRepository.save(UserSettings.builder().user(user).build());
        userStatsRepository.save(
                UserStats.builder().user(user).gems(100).updatedAt(Instant.now()).build());

        Token token =
                createToken(
                        user,
                        TokenType.EMAIL_VERIFICATION,
                        Duration.ofMillis(emailVerificationTokenExpiration));

        emailService.sendVerificationEmail(user.getEmail(), token.getToken());

        metricsService.recordUserRegistration("local");
        return generateTokens(user);
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication =
                authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(
                                request.identifier(), request.password()));

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userDetails.getUser();

        metricsService.recordUserLogin("local");
        return generateTokens(user);
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        Token refreshToken = getToken(request.refreshToken(), TokenType.REFRESH);
        validateExpiration(refreshToken);
        tokenRepository.delete(refreshToken);
        return generateTokens(refreshToken.getUser());
    }

    @Transactional
    public void verifyAccount(String token) {
        Token verificationToken = getToken(token, TokenType.EMAIL_VERIFICATION);
        validateExpiration(verificationToken);
        tokenRepository.delete(verificationToken);

        User user = verificationToken.getUser();
        user.setVerified(true);

        userRepository.save(user);
    }

    @Transactional
    public AuthResponse changePassword(ChangePasswordRequest request) {
        User user = getAuthenticatedUser();

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidInputException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new InvalidInputException("New password must be different from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        tokenRepository.deleteByUserAndType(user, TokenType.REFRESH);

        return generateTokens(user);
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        // Hash before looking the user up: BCrypt takes ~100 ms, and doing it only for existing
        // accounts would make them answer measurably slower than unknown emails.
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        String codeHash = passwordEncoder.encode(code);

        User user = userRepository.findByEmail(request.email()).orElse(null);

        if (user == null) {
            return;
        }

        tokenRepository.deleteByUserAndType(user, TokenType.PASSWORD_RESET);

        // Stored as a BCrypt hash (salted): a plain or unsalted hash of a 6-digit code would
        // collide
        // across users on the unique token column, and would be trivial to reverse.
        tokenRepository.save(
                Token.builder()
                        .token(codeHash)
                        .user(user)
                        .type(TokenType.PASSWORD_RESET)
                        .expiryDate(
                                Instant.now().plus(Duration.ofMillis(passwordResetTokenExpiration)))
                        .build());
        emailService.sendPasswordResetEmail(user.getEmail(), code);
    }

    // Not rolling back on a bad code keeps the failed attempt (or the deleted token) persisted.
    @Transactional(dontRollbackOn = InvalidResetCodeException.class)
    public void resetPassword(ResetPasswordRequest request) {
        User user =
                userRepository
                        .findByEmail(request.email())
                        .orElseThrow(InvalidResetCodeException::new);
        Token resetToken =
                tokenRepository
                        .findByUserAndType(user, TokenType.PASSWORD_RESET)
                        .orElseThrow(InvalidResetCodeException::new);

        if (resetToken.getExpiryDate().isBefore(Instant.now())) {
            tokenRepository.delete(resetToken);
            throw new InvalidResetCodeException();
        }

        if (!passwordEncoder.matches(request.code(), resetToken.getToken())) {
            resetToken.setAttempts(resetToken.getAttempts() + 1);
            if (resetToken.getAttempts() >= MAX_RESET_CODE_ATTEMPTS) {
                tokenRepository.delete(resetToken);
            } else {
                tokenRepository.save(resetToken);
            }
            throw new InvalidResetCodeException();
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new InvalidInputException("New password must be different from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        tokenRepository.delete(resetToken);
        tokenRepository.deleteByUserAndType(user, TokenType.REFRESH);
    }

    @Transactional
    public void resendVerificationEmail(ResendVerificationEmailRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);

        if (user == null) {
            return;
        }

        if (user.isVerified()) {
            return;
        }

        tokenRepository.deleteByUserAndType(user, TokenType.EMAIL_VERIFICATION);

        Token token =
                createToken(
                        user,
                        TokenType.EMAIL_VERIFICATION,
                        Duration.ofMillis(emailVerificationTokenExpiration));

        emailService.sendVerificationEmail(user.getEmail(), token.getToken());
    }

    private AuthResponse generateTokens(User user) {
        CustomUserDetails userDetails = new CustomUserDetails(user);

        String accessToken = jwtService.generateToken(userDetails);
        Token refreshToken =
                createToken(user, TokenType.REFRESH, Duration.ofMillis(refreshTokenExpiration));

        return new AuthResponse(accessToken, refreshToken.getToken());
    }

    @Transactional
    public AuthResponse authenticateWithGoogle(String idTokenString) {
        try {
            GoogleIdToken idToken = googleIdTokenVerifier.verify(idTokenString);

            if (idToken == null) {
                throw new InvalidCredentialsException("Invalid Google token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String familyName = (String) payload.get("family_name");
            String lastName = familyName != null ? familyName : "";

            Optional<User> userOptional = userRepository.findByEmail(email);
            User user;
            boolean isNewUser = userOptional.isEmpty();

            if (userOptional.isPresent()) {
                user = userOptional.get();
                boolean needsUpdate = false;

                // Google ya verifico el correo del usuario.
                if (!user.isVerified()) {
                    user.setVerified(true);
                    needsUpdate = true;
                }

                if (!user.getProviders().contains(AuthProvider.GOOGLE)) {
                    user.getProviders().add(AuthProvider.GOOGLE);
                    needsUpdate = true;
                }

                if (needsUpdate) {
                    user = userRepository.save(user);
                }
            } else {
                String cleanName = name.toLowerCase().replaceAll("\\s+", "");
                String generatedUsername = cleanName + ThreadLocalRandom.current().nextInt(10000);

                user =
                        User.builder()
                                .email(email)
                                .name(name)
                                .lastName(lastName)
                                .username(generatedUsername)
                                .passwordHash(null)
                                .role(Role.USER)
                                .enabled(true)
                                .verified(true)
                                .providers(new HashSet<>(Set.of(AuthProvider.GOOGLE)))
                                .build();

                user = userRepository.save(user);

                userSettingsRepository.save(UserSettings.builder().user(user).build());
                userStatsRepository.save(
                        UserStats.builder().user(user).gems(100).updatedAt(Instant.now()).build());
            }

            if (isNewUser) {
                metricsService.recordUserRegistration("google");
            } else {
                metricsService.recordUserLogin("google");
            }
            return generateTokens(user);
        } catch (InvalidCredentialsException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during Google authentication", e);
            throw new InvalidCredentialsException("Authentication failed");
        }
    }

    private Token getToken(String token, TokenType type) {
        return tokenRepository
                .findByTokenAndType(token, type)
                .orElseThrow(() -> new InvalidTokenException());
    }

    private void validateExpiration(Token token) {
        if (token.getExpiryDate().isBefore(Instant.now())) {
            tokenRepository.delete(token);
            throw new InvalidTokenException();
        }
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userDetails.getUser();
    }

    private Token createToken(User user, TokenType type, Duration duration) {
        Token token =
                Token.builder()
                        .token(UUID.randomUUID().toString())
                        .user(user)
                        .type(type)
                        .expiryDate(Instant.now().plus(duration))
                        .build();

        return tokenRepository.save(token);
    }
}
