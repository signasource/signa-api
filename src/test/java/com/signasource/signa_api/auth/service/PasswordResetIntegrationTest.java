package com.signasource.signa_api.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.google.firebase.messaging.FirebaseMessaging;
import com.signasource.signa_api.auth.dto.ForgotPasswordRequest;
import com.signasource.signa_api.auth.dto.ResetPasswordRequest;
import com.signasource.signa_api.auth.entity.Token;
import com.signasource.signa_api.auth.entity.TokenType;
import com.signasource.signa_api.auth.repository.TokenRepository;
import com.signasource.signa_api.exceptions.InvalidResetCodeException;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Over the real transaction (H2): a wrong code throws out of {@code resetPassword}, and the failed
 * attempt (or the discarded code) must still be committed instead of rolled back with it.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PasswordResetIntegrationTest {

    private static final String OLD_PASSWORD = "OldPassword1!";
    private static final String NEW_PASSWORD = "NewPassword1!";

    @MockitoBean private FirebaseMessaging firebaseMessaging;
    @MockitoBean private EmailService emailService;

    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private TokenRepository tokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User user;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user =
                userRepository.save(
                        User.builder()
                                .email(suffix + "@example.com")
                                .username("u" + suffix)
                                .passwordHash(passwordEncoder.encode(OLD_PASSWORD))
                                .name("Test")
                                .lastName("User")
                                .role(Role.USER)
                                .enabled(true)
                                .verified(true)
                                .build());
    }

    @Test
    void wrongCodePersistsTheFailedAttempt() {
        String code = requestCode();

        assertThatThrownBy(() -> reset(wrongCode(code)))
                .isInstanceOf(InvalidResetCodeException.class);

        assertThat(pendingCode()).get().extracting(Token::getAttempts).isEqualTo(1);
    }

    @Test
    void codeIsDiscardedAfterMaxFailedAttempts() {
        String code = requestCode();

        for (int i = 0; i < AuthService.MAX_RESET_CODE_ATTEMPTS; i++) {
            assertThatThrownBy(() -> reset(wrongCode(code)))
                    .isInstanceOf(InvalidResetCodeException.class);
        }

        assertThat(pendingCode()).isEmpty();
        // Even the right code no longer works once discarded.
        assertThatThrownBy(() -> reset(code)).isInstanceOf(InvalidResetCodeException.class);
    }

    @Test
    void correctCodeChangesPasswordAndConsumesIt() {
        String code = requestCode();

        reset(code);

        User updated = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, updated.getPasswordHash())).isTrue();
        assertThat(pendingCode()).isEmpty();
    }

    private String requestCode() {
        authService.forgotPassword(new ForgotPasswordRequest(user.getEmail()));
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq(user.getEmail()), code.capture());
        return code.getValue();
    }

    private void reset(String code) {
        authService.resetPassword(new ResetPasswordRequest(user.getEmail(), code, NEW_PASSWORD));
    }

    private static String wrongCode(String code) {
        return String.format("%06d", (Integer.parseInt(code) + 1) % 1_000_000);
    }

    private Optional<Token> pendingCode() {
        return tokenRepository.findByUserAndType(user, TokenType.PASSWORD_RESET);
    }
}
