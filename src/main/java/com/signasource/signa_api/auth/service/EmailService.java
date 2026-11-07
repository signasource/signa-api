package com.signasource.signa_api.auth.service;

import com.signasource.signa_api.common.MetricsService;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private static final String ADMIN_INVITE_PATH = "/accept-invite";
    private static final String SEAT_INVITATION_PATH = "/invitacion";
    private static final String PURCHASE_PATH = "/dashboard/purchases/";
    private static final DateTimeFormatter DEADLINE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy")
                    .withZone(ZoneId.of("America/Argentina/Buenos_Aires"));

    // Public page that copies the code to the clipboard (see CopyCodeController).
    private static final String COPY_CODE_PATH = "/codigo";
    private static final String PASSWORD_RESET_TEMPLATE = "email/password-reset.html";
    private static final String VERIFICATION_TEMPLATE = "email/verification.html";
    // Inline (CID) images: no public hosting needed and they render without "load images".
    private static final Map<String, String> PASSWORD_RESET_IMAGES =
            Map.of(
                    "signa-logo", "email/signa-logo.png",
                    "lisa", "email/lisa-arms-crossed.png",
                    "copy-icon", "email/copy-icon.png");
    private static final Map<String, String> VERIFICATION_IMAGES =
            Map.of(
                    "signa-logo", "email/signa-logo.png",
                    "lisa", "email/lisa-waving.png",
                    "copy-icon", "email/copy-icon.png");

    private final JavaMailSender mailSender;
    private final MetricsService metricsService;

    @Value("${app.panel-url:}")
    private String panelUrl;

    @Value("${app.base-url}")
    private String baseUrl;

    @Value("${app.mail.from:${spring.mail.username}}")
    private String fromEmail;

    @Async
    public void sendVerificationEmail(String to, String code) {
        sendEmail(
                to,
                "Verificá tu cuenta",
                renderCodeTemplate(VERIFICATION_TEMPLATE, code),
                VERIFICATION_IMAGES);
    }

    // Async so /auth/forgot-password answers in the same time (and status) whether or not the
    // email belongs to an account; sending inline made registered emails measurably slower.
    @Async
    public void sendPasswordResetEmail(String to, String code) {
        sendEmail(
                to,
                "Tu código para restablecer la contraseña",
                renderCodeTemplate(PASSWORD_RESET_TEMPLATE, code),
                PASSWORD_RESET_IMAGES);
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

    @Async
    public void sendSeatAccessEndedEmail(
            String to, String organizationName, String courseName, boolean hadAccess) {
        sendEmail(
                to,
                hadAccess
                        ? "Terminó tu acceso a " + courseName
                        : "Tu invitación a SIGNA fue anulada",
                buildSeatAccessEndedHtml(organizationName, courseName, hadAccess));
    }

    @Async
    public void sendPurchaseRefundedEmail(
            String to,
            String organizationName,
            String courseName,
            UUID purchaseId,
            String amount,
            String reasonText,
            int cancelledInvitations,
            int endedAccesses) {
        sendEmail(
                to,
                "Compra anulada: " + courseName,
                buildPurchaseRefundedHtml(
                        organizationName,
                        courseName,
                        String.format("%s%s%s", panelUrl, PURCHASE_PATH, purchaseId),
                        amount,
                        reasonText,
                        cancelledInvitations,
                        endedAccesses));
    }

    @Async
    public void sendOpsAlertEmail(String to, String subject, List<String> lines) {
        sendEmail(to, "[SIGNA] " + subject, buildOpsAlertHtml(lines));
    }

    private void sendEmail(String to, String subject, String html) {
        sendEmail(to, subject, html, Map.of());
    }

    private void sendEmail(
            String to, String subject, String html, Map<String, String> inlineImages) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            // Must come after setText so the images attach to the HTML part.
            for (Map.Entry<String, String> image : inlineImages.entrySet()) {
                helper.addInline(image.getKey(), new ClassPathResource(image.getValue()));
            }

            mailSender.send(message);

        } catch (Exception e) {
            // Sends run @Async, so this failure is otherwise invisible: the counter makes a mail
            // outage (e.g. the SMTP egress block) observable and alertable in Grafana.
            metricsService.recordEmailFailure();
            log.error("Error sending email to {}", to, e);
            throw new RuntimeException("Failed to send email");
        }
    }

    private String renderCodeTemplate(String template, String code) {
        StringBuilder cells = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            if (i > 0) {
                cells.append(
                        "<td class=\"sg-gap\" width=\"8\" style=\"width:8px;font-size:0;line-height:0;\">&nbsp;</td>\n");
            }
            cells.append(
                    ("<td class=\"sg-digit sg-d%d\" width=\"52\" height=\"64\" align=\"center\" valign=\"middle\" bgcolor=\"#FAF6F2\""
                                    + " style=\"width:52px;height:64px;background-color:#FAF6F2;border:2px solid #ECE5DE;border-radius:14px;"
                                    + "font-family:'Helvetica Neue',Helvetica,Arial,sans-serif;font-size:32px;line-height:64px;font-weight:800;"
                                    + "color:#241A16;mso-line-height-rule:exactly;\">%s</td>\n")
                            .formatted(
                                    i + 1, HtmlUtils.htmlEscape(String.valueOf(code.charAt(i)))));
        }
        return loadTemplate(template)
                .replace("{{DIGIT_CELLS}}", cells)
                .replace(
                        "{{COPY_URL}}",
                        HtmlUtils.htmlEscape(baseUrl + COPY_CODE_PATH + "#c=" + code))
                .replace("{{CODE_SPACED}}", HtmlUtils.htmlEscape(String.join(" ", code.split(""))))
                .replace("{{CODE}}", HtmlUtils.htmlEscape(code));
    }

    private String loadTemplate(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Missing email template " + path, e);
        }
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

    private String buildSeatAccessEndedHtml(
            String organizationName, String courseName, boolean hadAccess) {
        String body =
                hadAccess
                        ? "%s anuló la compra del curso <b>%s</b>, así que tu acceso terminó."
                                + " Tu progreso se conserva y lo podés seguir viendo en la app."
                        : "%s anuló la compra del curso <b>%s</b>, así que tu invitación ya no es"
                                + " válida. No tenés que hacer nada.";
        return """
				<div style="font-family: Arial;">
				    <h2>¡Hola!</h2>
				    <p>%s</p>
				    <p style="color:#666; font-size:12px;">Si tenés dudas, consultá con quien administra SIGNA en tu organización.</p>
				</div>
				"""
                .formatted(
                        body.formatted(
                                HtmlUtils.htmlEscape(organizationName),
                                HtmlUtils.htmlEscape(courseName)));
    }

    private String buildPurchaseRefundedHtml(
            String organizationName,
            String courseName,
            String link,
            String amount,
            String reasonText,
            int cancelledInvitations,
            int endedAccesses) {
        return """
				<div style="font-family: Arial;">
				    <h2>Compra anulada</h2>
				    <p>La compra del curso <b>%s</b> para %s quedó anulada. %s</p>
				    <p>Monto devuelto: <b>%s</b>. Mercado Pago lo acredita en el medio de pago original; según el medio puede tardar unos días.</p>
				    <ul>
				        <li>Invitaciones pendientes anuladas: %d</li>
				        <li>Personas que perdieron el acceso al curso: %d (su progreso se conserva)</li>
				    </ul>
				    <a href="%s" style="
				        background-color:#4CAF50;
				        color:white;
				        padding:10px 20px;
				        text-decoration:none;
				        border-radius:5px;">
				        Ver la compra
				    </a>
				</div>
				"""
                .formatted(
                        HtmlUtils.htmlEscape(courseName),
                        HtmlUtils.htmlEscape(organizationName),
                        HtmlUtils.htmlEscape(reasonText),
                        HtmlUtils.htmlEscape(amount),
                        cancelledInvitations,
                        endedAccesses,
                        link);
    }

    private String buildOpsAlertHtml(List<String> lines) {
        StringBuilder items = new StringBuilder();
        for (String line : lines) {
            items.append("<li>").append(HtmlUtils.htmlEscape(line)).append("</li>");
        }
        return """
				<div style="font-family: Arial;">
				    <ul>%s</ul>
				</div>
				"""
                .formatted(items);
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
