package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.service.EmailService;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.entity.RefundReason;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.service.PurchaseRevocationService.Revocation;
import com.signasource.signa_api.users.entity.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchaseNotificationServiceTest {

    @Mock private EmailService emailService;
    @Mock private OrganizationMemberRepository memberRepository;

    private CoursePurchase purchase;
    private Organization organization;

    @BeforeEach
    void setUp() {
        organization =
                Organization.builder()
                        .id(UUID.randomUUID())
                        .name("Hospital")
                        .contactEmail("contacto@hospital.com")
                        .build();
        purchase =
                CoursePurchase.builder()
                        .id(UUID.randomUUID())
                        .organization(organization)
                        .course(Course.builder().id(UUID.randomUUID()).name("LSA Salud").build())
                        .seats(2)
                        .currency("ARS")
                        .paymentReference("mercadopago:987")
                        .build();
    }

    private PurchaseNotificationService service(String opsEmail) {
        return new PurchaseNotificationService(emailService, memberRepository, opsEmail);
    }

    private void admins(String... emails) {
        when(memberRepository.findByOrganizationIdAndRoleAndStatus(
                        organization.getId(), MemberRole.ADMIN, MemberStatus.ACTIVE))
                .thenReturn(
                        java.util.Arrays.stream(emails)
                                .map(
                                        email -> {
                                            User user = new User();
                                            user.setEmail(email);
                                            return OrganizationMember.builder().user(user).build();
                                        })
                                .toList());
    }

    @Test
    void shouldTellEveryAffectedPersonTheAdminsAndSigna() {
        admins("admin@hospital.com");

        service("ops@signa.app")
                .purchaseRevoked(
                        purchase,
                        RefundReason.BUYER_REQUESTED,
                        new BigDecimal("243000"),
                        new Revocation(List.of("ana@x.com"), List.of("bruno@x.com")));

        verify(emailService).sendSeatAccessEndedEmail("ana@x.com", "Hospital", "LSA Salud", false);
        verify(emailService).sendSeatAccessEndedEmail("bruno@x.com", "Hospital", "LSA Salud", true);
        verify(emailService)
                .sendPurchaseRefundedEmail(
                        eq("admin@hospital.com"),
                        eq("Hospital"),
                        eq("LSA Salud"),
                        eq(purchase.getId()),
                        contains("243.000"),
                        contains("arrepentimiento"),
                        eq(1),
                        eq(1));
        verify(emailService)
                .sendOpsAlertEmail(eq("ops@signa.app"), contains("arrepentimiento"), anyList());
    }

    @Test
    void shouldFallBackToTheContactEmailWhenThereAreNoAdmins() {
        admins();

        service("")
                .purchaseRevoked(
                        purchase,
                        RefundReason.CHARGEBACK,
                        BigDecimal.TEN,
                        new Revocation(List.of(), List.of()));

        verify(emailService)
                .sendPurchaseRefundedEmail(
                        eq("contacto@hospital.com"),
                        any(),
                        any(),
                        any(),
                        any(),
                        contains("contracargo"),
                        anyInt(),
                        anyInt());
        verify(emailService, never()).sendSeatAccessEndedEmail(any(), any(), any(), anyBoolean());
    }

    @Test
    void shouldOnlyLogOpsAlertsWithoutAnInbox() {
        service(" ").alertOps("Disputa abierta", List.of("Compra: 1"));
        service(null).alertOps("Disputa abierta", List.of("Compra: 1"));

        verify(emailService, never()).sendOpsAlertEmail(anyString(), anyString(), anyList());
    }

    @Test
    void shouldEmailOpsAlertsToTheInbox() {
        service("ops@signa.app").alertOps("Reembolso parcial", List.of("Compra: 1"));

        verify(emailService)
                .sendOpsAlertEmail("ops@signa.app", "Reembolso parcial", List.of("Compra: 1"));
    }

    @Test
    void shouldFormatMoneyTheArgentineWay() {
        assertEquals(
                "$\u00a0243.000",
                PurchaseNotificationService.money(new BigDecimal("243000"), "ARS"));
    }
}
