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
	private final JavaMailSender mailSender;

	@Value("${app.base-url}")
	private String baseUrl;

	@Value("${spring.mail.username}")
	private String fromEmail;

	public void sendVerificationEmail(String to, String token) {
		String link = buildVerificationUrl(token);

		MimeMessage message = mailSender.createMimeMessage();

		try {
			createEmailMessage(message, to, link);
		} catch (MessagingException e) {
			throw new RuntimeException("Failed to create email message", e);
		}

		mailSender.send(message);
	}

	private String buildVerificationUrl(String token) {
		return baseUrl + "/auth/verify?token=" + token;
	}

	private void createEmailMessage(MimeMessage message, String to, String link) throws MessagingException {
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
}
