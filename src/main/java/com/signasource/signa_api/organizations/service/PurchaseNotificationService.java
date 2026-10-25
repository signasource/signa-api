package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.auth.service.EmailService;
import com.signasource.signa_api.common.AfterCommit;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.RefundReason;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.service.PurchaseRevocationService.Revocation;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Tells people about purchases whose money was given back: each affected employee, the
 * organization's admins, and Signa's operations inbox. Everything is sent after the transaction
 * commits; the operations alert is also logged, so it is never lost when no inbox is configured.
 */
@Slf4j
@Service
public class PurchaseNotificationService {

    private static final Locale ARGENTINA = Locale.forLanguageTag("es-AR");

    private final EmailService emailService;
    private final OrganizationMemberRepository memberRepository;
    private final String opsAlertEmail;

    public PurchaseNotificationService(
            EmailService emailService,
            OrganizationMemberRepository memberRepository,
            @Value("${organizations.purchases.ops-alert-email:}") String opsAlertEmail) {
        this.emailService = emailService;
        this.memberRepository = memberRepository;
        this.opsAlertEmail = opsAlertEmail;
    }

    public void purchaseRevoked(
            CoursePurchase purchase,
            RefundReason reason,
            BigDecimal refunded,
            Revocation revocation) {
        String organization = purchase.getOrganization().getName();
        String course = purchase.getCourse().getName();
        String amount = money(refunded, purchase.getCurrency());
        List<String> admins = adminEmails(purchase);

        AfterCommit.run(
                () -> {
                    revocation
                            .cancelledInvitations()
                            .forEach(
                                    email ->
                                            emailService.sendSeatAccessEndedEmail(
                                                    email, organization, course, false));
                    revocation
                            .endedAccesses()
                            .forEach(
                                    email ->
                                            emailService.sendSeatAccessEndedEmail(
                                                    email, organization, course, true));
                    admins.forEach(
                            email ->
                                    emailService.sendPurchaseRefundedEmail(
                                            email,
                                            organization,
                                            course,
                                            purchase.getId(),
                                            amount,
                                            reasonText(reason),
                                            revocation.cancelledInvitations().size(),
                                            revocation.endedAccesses().size()));
                });

        alertOps(
                "Compra anulada (" + reasonLabel(reason) + "): " + organization,
                List.of(
                        "Organización: " + organization,
                        "Curso: " + course + " · " + purchase.getSeats() + " cupos",
                        "Compra: " + purchase.getId(),
                        "Pago: " + purchase.getPaymentReference(),
                        "Monto devuelto: " + amount,
                        "Invitaciones anuladas: " + revocation.cancelledInvitations().size(),
                        "Accesos cortados: " + revocation.endedAccesses().size()));
    }

    /** A situation that needs a person: logged always, emailed when an inbox is configured. */
    public void alertOps(String subject, List<String> lines) {
        log.warn("{} | {}", subject, String.join(" | ", lines));
        if (opsAlertEmail == null || opsAlertEmail.isBlank()) {
            return;
        }
        String to = opsAlertEmail;
        AfterCommit.run(() -> emailService.sendOpsAlertEmail(to, subject, lines));
    }

    private List<String> adminEmails(CoursePurchase purchase) {
        List<String> emails = new ArrayList<>();
        memberRepository
                .findByOrganizationIdAndRoleAndStatus(
                        purchase.getOrganization().getId(), MemberRole.ADMIN, MemberStatus.ACTIVE)
                .forEach(member -> emails.add(member.getUser().getEmail()));
        String contact = purchase.getOrganization().getContactEmail();
        if (emails.isEmpty() && contact != null && !contact.isBlank()) {
            emails.add(contact);
        }
        return emails;
    }

    static String money(BigDecimal amount, String currency) {
        NumberFormat format = NumberFormat.getCurrencyInstance(ARGENTINA);
        format.setCurrency(Currency.getInstance(currency));
        format.setMaximumFractionDigits(0);
        return format.format(amount);
    }

    private static String reasonText(RefundReason reason) {
        return switch (reason) {
            case BUYER_REQUESTED ->
                    "La anularon desde el panel dentro del plazo de arrepentimiento.";
            case EXTERNAL_REFUND -> "El pago se devolvió desde Mercado Pago.";
            case CHARGEBACK ->
                    "El pago fue desconocido ante el banco o la tarjeta (contracargo) y Mercado"
                            + " Pago devolvió el dinero.";
            default -> "El pago se devolvió.";
        };
    }

    private static String reasonLabel(RefundReason reason) {
        return switch (reason) {
            case BUYER_REQUESTED -> "arrepentimiento";
            case EXTERNAL_REFUND -> "reembolso desde Mercado Pago";
            case CHARGEBACK -> "contracargo";
            default -> reason.name();
        };
    }
}
