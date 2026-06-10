package com.signasource.signa_api.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
	private static final String VERIFICATION_PATH = "/auth/verify";
	private static final String PASSWORD_RESET_PATH = "/auth/reset-password";

	private final JavaMailSender mailSender;

	@Value("${app.base-url}")
	private String baseUrl;

	@Value("${spring.mail.username}")
	private String fromEmail;

	public void sendVerificationEmail(String to, String token) {
		sendEmail(to, "Verify your account", buildVerificationHtml(buildUrl(VERIFICATION_PATH, token)));
	}

	public void sendPasswordResetEmail(String to, String token) {
		sendEmail(to, "Reset your password", buildPasswordResetHtml(buildUrl(PASSWORD_RESET_PATH, token)));
	}

	private void sendEmail(String to, String subject, String html) {
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true);

			helper.setFrom(fromEmail);
			helper.setTo(to);
			helper.setSubject(subject);
			helper.setText(html, true);

			mailSender.send(message);

		} catch (Exception e) {
			log.error("Error sending email to {}", to, e);
			throw new RuntimeException("Failed to send email");
		}
	}

	private String buildUrl(String path, String token) {
		return String.format("%s%s?token=%s", baseUrl, path, token);
	}

	private String buildVerificationHtml(String link) {
		return """
				<div style="font-family: Arial;">
				    <h2>Bienvenido/a 👋</h2>
				    <p>Por favor, verifica tu cuenta:</p>
				    <a href="%s" style="
				        background-color:#4CAF50;
				        color:white;
				        padding:10px 20px;
				        text-decoration:none;
				        border-radius:5px;">
				        Verificar cuenta
				    </a>
				</div>
				""".formatted(link);
	}

	private String buildPasswordResetHtml(String link) {
		return """
				<div style="font-family: Arial;">
				    <h2>¡Hola!</h2>
				    <p>Podés resetear tu contraseña acá:</p>
				    <a href="%s" style="
				        background-color:#4CAF50;
				        color:white;
				        padding:10px 20px;
				        text-decoration:none;
				        border-radius:5px;">
				        Resetear contraseña
				    </a>
				</div>
				""".formatted(link);
	}
}
