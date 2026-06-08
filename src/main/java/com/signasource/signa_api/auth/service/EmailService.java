package com.signasource.signa_api.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailService {
	private static final String VERIFICATION_PATH = "/auth/verify";
	private static final String PASSWORD_RESET_PATH = "/auth/reset-password";

	private final JavaMailSender mailSender;

	@Value("${app.base-url}")
	private String baseUrl;

	@Value("${spring.mail.username}")
	private String fromEmail;

	public void sendVerificationEmail(String to, String token) {
		String link = buildUrl(VERIFICATION_PATH, token);

		MimeMessage message = mailSender.createMimeMessage();

		try {
			createVerificationMessage(message, to, link);
		} catch (MessagingException e) {
			throw new RuntimeException("Failed to create email message", e);
		}

		mailSender.send(message);
	}

	public void sendPasswordResetEmail(String to, String token) {
		String link = buildUrl(PASSWORD_RESET_PATH, token);

		MimeMessage message = mailSender.createMimeMessage();

		try {
			createPasswordResetMessage(message, to, link);
		} catch (MessagingException e) {
			throw new RuntimeException("Failed to create email message", e);
		}

		mailSender.send(message);
	}

	private String buildUrl(String path, String token) {
		return String.format("%s%s?token=%s", baseUrl, path, token);
	}

	private void createVerificationMessage(MimeMessage message, String to, String link) throws MessagingException {
		MimeMessageHelper helper = new MimeMessageHelper(message, true);

		helper.setFrom(fromEmail);
		helper.setTo(to);
		helper.setSubject("Verify your account");

		String html = """
				<div style="font-family: Arial;">
				    <h2>Bienvenido/a 👋</h2>
				    <p>Por favor, verifica tu cuenta:</p>
				    <a href="%s" style="
				        background-color:#4CAF50;
				        color:white;
				        padding:10px 20px;
				        text-decoration:none;
				        border-radius:5px;">
				        Verify Account
				    </a>
				</div>
				""".formatted(link);

		helper.setText(html, true);
	}

	private void createPasswordResetMessage(MimeMessage message, String to, String link) throws MessagingException {
		MimeMessageHelper helper = new MimeMessageHelper(message, true);

		helper.setFrom(fromEmail);
		helper.setTo(to);
		helper.setSubject("Reset your password");

		String html = """
				<div style="font-family: Arial;">
				    <h2>Reset your password</h2>
				    <p>Click the link below to reset your password:</p>
				    <a href="%s" style="
				        background-color:#4CAF50;
				        color:white;
				        padding:10px 20px;
				        text-decoration:none;
				        border-radius:5px;">
				        Reset Password
				    </a>
				</div>
				""".formatted(link);

		helper.setText(html, true);
	}
}
