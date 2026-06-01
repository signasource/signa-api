package com.signasource.signa_api.auth.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.mail.internet.MimeMessage;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

	private static final String BASE_URL = "https://example.com";
	private static final String FROM_EMAIL = "noreply@example.com";

	private static final String TO_EMAIL = "user@example.com";
	private static final String TOKEN = "verification-token";

	@Mock
	private JavaMailSender mailSender;

	@Mock
	private MimeMessage mimeMessage;

	@InjectMocks
	private EmailService emailService;

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
}
