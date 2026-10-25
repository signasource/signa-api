package com.signasource.signa_api.auth.service;

import jakarta.mail.internet.MimeMessage;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private static final String VERIFICATION_PATH = "/auth/verify";
    private static final String ADMIN_INVITE_PATH = "/accept-invite";
    private static final String SEAT_INVITATION_PATH = "/invitacion";
    private static final DateTimeFormatter DEADLINE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
                    .withZone(ZoneId.of("America/Argentina/Buenos_Aires"));

    private final JavaMailSender mailSender;

    @Value("${app.panel-url:}")
    private String panelUrl;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Async
    public void sendVerificationEmail(String to, String token) {
        sendEmail(
                to,
                "Verificá tu cuenta",
                buildVerificationHtml(buildUrl(VERIFICATION_PATH, token)));
    }

    // Async so /auth/forgot-password answers in the same time (and status) whether or not the
    // email belongs to an account; sending inline made registered emails measurably slower.
    @Async
    public void sendPasswordResetEmail(String to, String code) {
        sendEmail(to, "Tu código para restablecer la contraseña", buildPasswordResetHtml(code));
    }

    @Async
    public void sendSeatInvitationEmail(
            String to,
            String organizationName,
            String courseName,
            String token,
            Instant deadline,
            int durationDays,
            boolean reminder) {
        String link = String.format("%s%s?token=%s", panelUrl, SEAT_INVITATION_PATH, token);
        sendEmail(
                to,
                reminder
                        ? "Recordatorio: tu invitación a SIGNA vence pronto"
                        : "Te invitaron a SIGNA",
                buildSeatInvitationHtml(
                        organizationName,
                        courseName,
                        link,
                        DEADLINE_FORMAT.format(deadline),
                        durationDays,
                        reminder));
    }

    @Async
    public void sendOrganizationAdminInviteEmail(String to, String organizationName, String code) {
        sendEmail(
                to,
                "Te invitaron a administrar una organización en SIGNA",
                buildOrganizationAdminInviteHtml(
                        organizationName,
                        String.format("%s%s?code=%s", panelUrl, ADMIN_INVITE_PATH, code)));
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
				"""
                .formatted(link);
    }

    private String buildPasswordResetHtml(String code) {
        return """
				<div style="font-family: Arial;">
				    <h2>¡Hola!</h2>
				    <p>Ingresá este código en la app para restablecer tu contraseña:</p>
				    <p style="font-size:32px; font-weight:bold; letter-spacing:8px;">%s</p>
				    <p>Vence en 15 minutos. Si no lo pediste, ignorá este correo.</p>
				</div>
				"""
                .formatted(code);
    }

    private String buildSeatInvitationHtml(
            String organizationName,
            String courseName,
            String link,
            String deadline,
            int durationDays,
            boolean reminder) {
        String intro =
                reminder
                        ? "Todavía no aceptaste la invitación de %s al curso <b>%s</b> de SIGNA."
                        : "%s te invitó al curso <b>%s</b> de SIGNA para aprender Lengua de Señas Argentina.";
        return """
				<div style="font-family: Arial;">
				    <h2>¡Hola!</h2>
				    <p>%s</p>
				    <p>Tenés %d días de acceso desde que aceptás. La invitación vence el %s.</p>
				    <p>Creá tu cuenta o iniciá sesión con este mismo email y aceptá la invitación:</p>
				    <a href="%s" style="
				        background-color:#4CAF50;
				        color:white;
				        padding:10px 20px;
				        text-decoration:none;
				        border-radius:5px;">
				        Aceptar invitación
				    </a>
				    <p style="color:#666; font-size:12px;">La invitación es personal: solo funciona con la cuenta de este email.</p>
				</div>
				"""
                .formatted(
                        intro.formatted(
                                HtmlUtils.htmlEscape(organizationName),
                                HtmlUtils.htmlEscape(courseName)),
                        durationDays,
                        deadline,
                        link);
    }

    private String buildOrganizationAdminInviteHtml(String organizationName, String link) {
        return """
				<div style="font-family: Arial;">
				    <h2>¡Hola!</h2>
				    <p>%s te invitó a administrar su organización en SIGNA.</p>
				    <p>Creá tu cuenta o iniciá sesión en el panel y aceptá la invitación:</p>
				    <a href="%s" style="
				        background-color:#4CAF50;
				        color:white;
				        padding:10px 20px;
				        text-decoration:none;
				        border-radius:5px;">
				        Aceptar invitación
				    </a>
				</div>
				"""
                .formatted(HtmlUtils.htmlEscape(organizationName), link);
    }
}
