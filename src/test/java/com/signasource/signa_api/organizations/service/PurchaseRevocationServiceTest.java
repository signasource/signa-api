package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.entity.RefundReason;
import com.signasource.signa_api.organizations.entity.SeatStatus;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.repository.PurchaseSeatRepository;
import com.signasource.signa_api.organizations.service.PurchaseRevocationService.Revocation;
import com.signasource.signa_api.users.entity.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchaseRevocationServiceTest {

    @Mock private CoursePurchaseRepository purchaseRepository;
    @Mock private PurchaseSeatRepository seatRepository;
    @Mock private OrganizationEnrollmentService enrollmentService;
    @Mock private PurchaseNotificationService notifications;

    @InjectMocks private PurchaseRevocationService service;

    private CoursePurchase purchase;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.now();
        Instant paidAt = now.minus(2, ChronoUnit.DAYS);
        purchase =
                CoursePurchase.builder()
                        .id(UUID.randomUUID())
                        .organization(
                                Organization.builder()
                                        .id(UUID.randomUUID())
                                        .name("Hospital")
                                        .build())
                        .course(Course.builder().id(UUID.randomUUID()).name("LSA Salud").build())
                        .status(PurchaseStatus.PAID)
                        .paidAt(paidAt)
                        .invitationDeadline(paidAt.plus(10, ChronoUnit.DAYS))
                        .build();
    }

    private PurchaseSeat seat(String email, SeatStatus status) {
        return PurchaseSeat.builder()
                .id(UUID.randomUUID())
                .purchase(purchase)
                .email(email)
                .status(status)
                .invitationToken(status == SeatStatus.INVITED ? "tok-" + email : null)
                .build();
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }

    @Test
    void shouldCancelPendingInvitationsAndEndActiveAccesses() {
        PurchaseSeat invited = seat("ana@x.com", SeatStatus.INVITED);
        PurchaseSeat active = seat("bruno@x.com", SeatStatus.ACTIVE);
        User bruno = user();
        active.setUser(bruno);
        active.setAccessExpiresAt(now.plus(20, ChronoUnit.DAYS));
        when(seatRepository.findForUpdateByPurchaseId(purchase.getId()))
                .thenReturn(List.of(invited, active));

        Revocation revocation =
                service.revoke(purchase, RefundReason.BUYER_REQUESTED, BigDecimal.TEN, now);

        assertEquals(PurchaseStatus.REFUNDED, purchase.getStatus());
        assertEquals(now, purchase.getRefundedAt());
        verify(purchaseRepository).save(purchase);

        assertEquals(SeatStatus.CANCELLED, invited.getStatus());
        assertNull(invited.getInvitationToken());
        assertEquals(SeatStatus.REVOKED, active.getStatus());
        assertEquals(now, active.getRevokedAt());
        verify(enrollmentService)
                .endCourseAccess(
                        bruno,
                        purchase.getOrganization().getId(),
                        purchase.getCourse().getId(),
                        now);

        assertEquals(List.of("ana@x.com"), revocation.cancelledInvitations());
        assertEquals(List.of("bruno@x.com"), revocation.endedAccesses());
        verify(notifications)
                .purchaseRevoked(
                        purchase, RefundReason.BUYER_REQUESTED, BigDecimal.TEN, revocation);
    }

    @Test
    void shouldLeaveSeatsThatAlreadyEndedAsTheyAre() {
        PurchaseSeat expiredAccess = seat("caro@x.com", SeatStatus.ACTIVE);
        expiredAccess.setUser(user());
        expiredAccess.setAccessExpiresAt(now.minus(1, ChronoUnit.DAYS));
        PurchaseSeat removed = seat("dani@x.com", SeatStatus.REVOKED);
        PurchaseSeat expiredInvitation = seat("eva@x.com", SeatStatus.INVITED);
        purchase.setInvitationDeadline(now.minus(1, ChronoUnit.HOURS));
        when(seatRepository.findForUpdateByPurchaseId(purchase.getId()))
                .thenReturn(List.of(expiredAccess, removed, expiredInvitation));

        Revocation revocation = service.revoke(purchase, RefundReason.CHARGEBACK, null, now);

        assertEquals(SeatStatus.ACTIVE, expiredAccess.getStatus());
        assertEquals(SeatStatus.REVOKED, removed.getStatus());
        assertEquals(SeatStatus.INVITED, expiredInvitation.getStatus());
        verify(seatRepository, never()).save(any());
        verify(enrollmentService, never()).endCourseAccess(any(), any(), any(), any());
        assertEquals(List.of(), revocation.cancelledInvitations());
        assertEquals(List.of(), revocation.endedAccesses());
    }

    @Test
    void shouldRevokeAnActiveSeatEvenWithoutItsUser() {
        PurchaseSeat orphan = seat("borrada@x.com", SeatStatus.ACTIVE);
        when(seatRepository.findForUpdateByPurchaseId(purchase.getId()))
                .thenReturn(List.of(orphan));

        service.revoke(purchase, RefundReason.EXTERNAL_REFUND, BigDecimal.ONE, now);

        assertEquals(SeatStatus.REVOKED, orphan.getStatus());
        verify(enrollmentService, never()).endCourseAccess(any(), any(), any(), any());
        verify(notifications)
                .purchaseRevoked(eq(purchase), eq(RefundReason.EXTERNAL_REFUND), any(), any());
    }
}
