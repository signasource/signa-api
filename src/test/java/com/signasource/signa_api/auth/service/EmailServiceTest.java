package com.signasource.signa_api.auth.service;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    private static final String BASE_URL = "https://example.com";
    private static final String FROM_EMAIL = "noreply@example.com";

    private static final String TO_EMAIL = "user@example.com";
    private static final String TOKEN = "verification-token";

    @Mock private JavaMailSender mailSender;

    @Mock private MimeMessage mimeMessage;

    @InjectMocks private EmailService emailService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(emailService, "fromEmail", FROM_EMAIL);

        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
    }

    @Test
    void testSendVerificationEmail() {
        emailService.sendVerificationEmail(TO_EMAIL, TOKEN);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void testSendResetPasswordEmail() {
        emailService.sendPasswordResetEmail(TO_EMAIL, TOKEN);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void testSendOrganizationAdminInviteEmail() {
        emailService.sendOrganizationAdminInviteEmail(
                TO_EMAIL, "Hospital <San Martin>", "ABCD2345");

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void testSendSeatInvitationEmail() {
        emailService.sendSeatInvitationEmail(
                TO_EMAIL, "Hospital <San Martin>", "LSA Salud", "tok", Instant.now(), 30, false);
        emailService.sendSeatInvitationEmail(
                TO_EMAIL, "Hospital <San Martin>", "LSA Salud", "tok", Instant.now(), 30, true);

        verify(mailSender, times(2)).send(mimeMessage);
    }

    @Test
    void testSendSeatAccessEndedEmail() {
        emailService.sendSeatAccessEndedEmail(TO_EMAIL, "Hospital <San Martin>", "LSA Salud", true);
        emailService.sendSeatAccessEndedEmail(
                TO_EMAIL, "Hospital <San Martin>", "LSA Salud", false);

        verify(mailSender, times(2)).send(mimeMessage);
    }

    @Test
    void testSendPurchaseRefundedEmail() {
        emailService.sendPurchaseRefundedEmail(
                TO_EMAIL,
                "Hospital <San Martin>",
                "LSA Salud",
                java.util.UUID.randomUUID(),
                "$ 243.000",
                "La anularon desde el panel.",
                3,
                2);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void testSendOpsAlertEmail() {
        emailService.sendOpsAlertEmail(
                TO_EMAIL, "Contracargo: Hospital", java.util.List.of("Compra: <1>", "Pago: 987"));

        verify(mailSender).send(mimeMessage);
    }
}
