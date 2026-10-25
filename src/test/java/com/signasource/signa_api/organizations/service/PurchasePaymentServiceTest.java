package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.admin.dto.AdminPurchaseResponse;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationCourse;
import com.signasource.signa_api.organizations.entity.PurchaseRefund;
import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.entity.RefundReason;
import com.signasource.signa_api.organizations.entity.RefundStatus;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.repository.OrganizationCourseRepository;
import com.signasource.signa_api.organizations.repository.PurchaseRefundRepository;
import com.signasource.signa_api.organizations.repository.PurchaseSeatRepository;
import com.signasource.signa_api.organizations.service.PurchasePaymentService.Reversal;
import com.signasource.signa_api.organizations.service.PurchasePaymentService.Settlement;
import com.signasource.signa_api.users.entity.User;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PurchasePaymentServiceTest {

    @Mock private CoursePurchaseRepository purchaseRepository;
    @Mock private PurchaseSeatRepository seatRepository;
    @Mock private PurchaseRefundRepository refundRepository;
    @Mock private OrganizationCourseRepository organizationCourseRepository;
    @Mock private SeatInvitationService invitationService;
    @Mock private CoursePurchaseService purchaseService;
    @Mock private PurchaseRevocationService revocationService;

    private PurchasePaymentService service;
    private CoursePurchase purchase;
    private Organization organization;
    private Course course;

    @BeforeEach
    void setUp() {
        PurchasePolicy policy = new PurchasePolicy(15, 60, 10, 5, 24, 500, 10);
        service =
                new PurchasePaymentService(
                        purchaseRepository,
                        seatRepository,
                        refundRepository,
                        organizationCourseRepository,
                        invitationService,
                        purchaseService,
                        revocationService,
                        policy);
        organization = Organization.builder().id(UUID.randomUUID()).name("Hospital").build();
        course = Course.builder().id(UUID.randomUUID()).name("LSA Salud").build();
        purchase =
                CoursePurchase.builder()
                        .id(UUID.randomUUID())
                        .organization(organization)
                        .course(course)
                        .seats(1)
                        .durationDays(30)
                        .listPricePerSeat(BigDecimal.TEN)
                        .pricePerSeat(BigDecimal.TEN)
                        .totalAmount(BigDecimal.TEN)
                        .currency("ARS")
                        .build();
    }

    @Test
    void shouldMarkPaidOpenTheWindowContractTheCourseAndInvite() {
        PurchaseSeat seat = PurchaseSeat.builder().purchase(purchase).email("a@x.com").build();
        when(purchaseRepository.findForUpdateById(purchase.getId()))
                .thenReturn(Optional.of(purchase));
        when(organizationCourseRepository.existsByOrganizationIdAndCourseId(
                        organization.getId(), course.getId()))
                .thenReturn(false);
        when(seatRepository.findByPurchaseIdOrderByEmailAsc(purchase.getId()))
                .thenReturn(List.of(seat));

        service.confirmPayment(purchase.getId(), "mp-123");

        assertEquals(PurchaseStatus.PAID, purchase.getStatus());
        assertEquals("mp-123", purchase.getPaymentReference());
        assertEquals(
                Duration.ofDays(10),
                Duration.between(purchase.getPaidAt(), purchase.getInvitationDeadline()));
        verify(organizationCourseRepository).save(any(OrganizationCourse.class));
        verify(invitationService).invite(eq(seat), eq(purchase.getPaidAt()));
        verify(purchaseService).describe(purchase);
    }

    @Test
    void shouldNotContractTheCourseTwice() {
        when(purchaseRepository.findForUpdateById(purchase.getId()))
                .thenReturn(Optional.of(purchase));
        when(organizationCourseRepository.existsByOrganizationIdAndCourseId(
                        organization.getId(), course.getId()))
                .thenReturn(true);
        when(seatRepository.findByPurchaseIdOrderByEmailAsc(purchase.getId()))
                .thenReturn(List.of());

        service.confirmPayment(purchase.getId(), null);

        verify(organizationCourseRepository, never()).save(any());
    }

    @Test
    void shouldBeIdempotentForAPaidPurchase() {
        purchase.setStatus(PurchaseStatus.PAID);
        when(purchaseRepository.findForUpdateById(purchase.getId()))
                .thenReturn(Optional.of(purchase));

        service.confirmPayment(purchase.getId(), "mp-123");

        verify(invitationService, never()).invite(any(), any());
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void shouldRejectACancelledPurchase() {
        purchase.setStatus(PurchaseStatus.CANCELLED);
        when(purchaseRepository.findForUpdateById(purchase.getId()))
                .thenReturn(Optional.of(purchase));

        assertThrows(
                InvalidInputException.class, () -> service.confirmPayment(purchase.getId(), null));
    }

    @Test
    void shouldReturnNotFoundForAnUnknownPurchase() {
        UUID unknown = UUID.randomUUID();
        when(purchaseRepository.findForUpdateById(unknown)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.confirmPayment(unknown, null));
    }

    @Test
    void shouldListPurchasesForTheBackOffice() {
        when(purchaseRepository.findByStatusOrderByCreatedAtAsc(PurchaseStatus.PENDING_PAYMENT))
                .thenReturn(List.of(purchase));
        when(purchaseRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(seatRepository.findByPurchaseIdOrderByEmailAsc(purchase.getId()))
                .thenReturn(List.of());

        List<AdminPurchaseResponse> pending = service.listForAdmin(PurchaseStatus.PENDING_PAYMENT);
        List<AdminPurchaseResponse> all = service.listForAdmin(null);

        assertEquals("Hospital", pending.get(0).organizationName());
        assertEquals(0, all.size());
    }

    private void stubLockedPurchase() {
        when(refundRepository.findByPaymentId("987")).thenReturn(Optional.empty());
        when(purchaseRepository.findForUpdateById(purchase.getId()))
                .thenReturn(Optional.of(purchase));
    }

    private RefundReason recordedReason() {
        ArgumentCaptor<PurchaseRefund> refund = ArgumentCaptor.forClass(PurchaseRefund.class);
        verify(refundRepository).save(refund.capture());
        assertEquals("987", refund.getValue().getPaymentId());
        assertEquals(RefundStatus.PENDING, refund.getValue().getStatus());
        assertEquals(purchase, refund.getValue().getPurchase());
        return refund.getValue().getReason();
    }

    @Test
    void shouldSettleAMatchingPaymentByConfirmingThePurchase() {
        stubLockedPurchase();
        when(seatRepository.findByPurchaseIdOrderByEmailAsc(purchase.getId()))
                .thenReturn(List.of());

        Settlement settlement = service.settle(purchase.getId(), "987", BigDecimal.TEN, "ARS");

        assertEquals(Settlement.CONFIRMED, settlement);
        assertEquals(PurchaseStatus.PAID, purchase.getStatus());
        assertEquals("mercadopago:987", purchase.getPaymentReference());
        verify(refundRepository, never()).save(any());
    }

    @Test
    void shouldIgnoreARepeatedNotificationOfTheConfirmingPayment() {
        stubLockedPurchase();
        purchase.setStatus(PurchaseStatus.PAID);
        purchase.setPaymentReference("mercadopago:987");

        assertEquals(
                Settlement.ALREADY_CONFIRMED,
                service.settle(purchase.getId(), "987", BigDecimal.TEN, "ARS"));
        verify(refundRepository, never()).save(any());
    }

    @Test
    void shouldRefundAPaymentForACancelledPurchase() {
        stubLockedPurchase();
        purchase.setStatus(PurchaseStatus.CANCELLED);

        assertEquals(
                Settlement.REFUND, service.settle(purchase.getId(), "987", BigDecimal.TEN, "ARS"));
        assertEquals(RefundReason.PURCHASE_CANCELLED, recordedReason());
        assertEquals(PurchaseStatus.CANCELLED, purchase.getStatus());
    }

    @Test
    void shouldRefundASecondPaymentForAPaidPurchase() {
        stubLockedPurchase();
        purchase.setStatus(PurchaseStatus.PAID);
        purchase.setPaymentReference("mercadopago:111");

        assertEquals(
                Settlement.REFUND, service.settle(purchase.getId(), "987", BigDecimal.TEN, "ARS"));
        assertEquals(RefundReason.ALREADY_PAID, recordedReason());
        assertEquals("mercadopago:111", purchase.getPaymentReference());
    }

    @Test
    void shouldRefundAPaymentOfAnotherAmountOrCurrencyWithoutConfirming() {
        stubLockedPurchase();

        assertEquals(
                Settlement.REFUND, service.settle(purchase.getId(), "987", BigDecimal.ONE, "ARS"));
        assertEquals(RefundReason.AMOUNT_MISMATCH, recordedReason());
        assertEquals(PurchaseStatus.PENDING_PAYMENT, purchase.getStatus());
    }

    @Test
    void shouldRecordTheRefundEvenWithoutAmountOrCurrency() {
        stubLockedPurchase();

        assertEquals(Settlement.REFUND, service.settle(purchase.getId(), "987", null, null));
        ArgumentCaptor<PurchaseRefund> refund = ArgumentCaptor.forClass(PurchaseRefund.class);
        verify(refundRepository).save(refund.capture());
        assertEquals(BigDecimal.ZERO, refund.getValue().getAmount());
        assertEquals("ARS", refund.getValue().getCurrency());
    }

    @Test
    void shouldNotRecordARefundTwice() {
        PurchaseRefund pending = PurchaseRefund.builder().paymentId("987").build();
        when(refundRepository.findByPaymentId("987")).thenReturn(Optional.of(pending));

        assertEquals(
                Settlement.REFUND, service.settle(purchase.getId(), "987", BigDecimal.TEN, "ARS"));

        pending.setStatus(RefundStatus.REFUNDED);
        assertEquals(
                Settlement.ALREADY_REFUNDED,
                service.settle(purchase.getId(), "987", BigDecimal.TEN, "ARS"));
        verify(refundRepository, never()).save(any());
        verify(purchaseRepository, never()).findForUpdateById(any());
    }

    @Test
    void shouldReportAnUnknownPurchase() {
        when(refundRepository.findByPaymentId("987")).thenReturn(Optional.empty());
        UUID unknown = UUID.randomUUID();
        when(purchaseRepository.findForUpdateById(unknown)).thenReturn(Optional.empty());

        assertEquals(
                Settlement.UNKNOWN_PURCHASE, service.settle(unknown, "987", BigDecimal.TEN, "ARS"));
    }

    @Test
    void shouldMarkAPendingRefundAsDone() {
        PurchaseRefund pending =
                PurchaseRefund.builder()
                        .paymentId("987")
                        .reason(RefundReason.ALREADY_PAID)
                        .purchase(purchase)
                        .build();
        when(refundRepository.findByPaymentId("987")).thenReturn(Optional.of(pending));

        service.markRefunded("987", "r-1");

        assertEquals(RefundStatus.REFUNDED, pending.getStatus());
        assertEquals("r-1", pending.getMercadoPagoRefundId());
        assertNotNull(pending.getRefundedAt());
        verify(refundRepository).save(pending);
    }

    @Test
    void shouldKeepTheRefundIdWhenMarkedFromANotification() {
        PurchaseRefund pending =
                PurchaseRefund.builder()
                        .paymentId("987")
                        .reason(RefundReason.ALREADY_PAID)
                        .purchase(purchase)
                        .mercadoPagoRefundId("r-1")
                        .build();
        when(refundRepository.findByPaymentId("987")).thenReturn(Optional.of(pending));

        service.markRefunded("987", null);

        assertEquals("r-1", pending.getMercadoPagoRefundId());
    }

    @Test
    void shouldIgnoreMarkingAPaymentThatWasNeverToBeRefunded() {
        when(refundRepository.findByPaymentId("987")).thenReturn(Optional.empty());
        PurchaseRefund done =
                PurchaseRefund.builder().paymentId("555").status(RefundStatus.REFUNDED).build();
        when(refundRepository.findByPaymentId("555")).thenReturn(Optional.of(done));

        service.markRefunded("987", "r-1");
        service.markRefunded("555", "r-2");

        verify(refundRepository, never()).save(any());
    }

    private User actor() {
        User actor = new User();
        actor.setId(UUID.randomUUID());
        return actor;
    }

    private void paidWithMercadoPago(Instant refundableUntil) {
        purchase.setStatus(PurchaseStatus.PAID);
        purchase.setPaymentReference("mercadopago:987");
        purchase.setRefundableUntil(refundableUntil);
        when(purchaseRepository.findForUpdateById(purchase.getId()))
                .thenReturn(Optional.of(purchase));
    }

    @Test
    void shouldOpenTheWithdrawalPeriodWhenMercadoPagoConfirmsThePayment() {
        stubLockedPurchase();
        when(seatRepository.findByPurchaseIdOrderByEmailAsc(purchase.getId()))
                .thenReturn(List.of());
        Instant before = Instant.now();

        service.settle(purchase.getId(), "987", BigDecimal.TEN, "ARS");

        assertNotNull(purchase.getRefundableUntil());
        assertTrue(!purchase.getRefundableUntil().isBefore(before.plus(Duration.ofDays(10))));
    }

    @Test
    void shouldNotOfferAWithdrawalForAPurchaseConfirmedByHand() {
        when(purchaseRepository.findForUpdateById(purchase.getId()))
                .thenReturn(Optional.of(purchase));
        when(seatRepository.findByPurchaseIdOrderByEmailAsc(purchase.getId()))
                .thenReturn(List.of());

        service.confirmPayment(purchase.getId(), "TRANSFERENCIA-12");

        assertNull(purchase.getRefundableUntil());
    }

    @Test
    void shouldNotConfirmARefundedPurchaseByHand() {
        purchase.setStatus(PurchaseStatus.REFUNDED);
        when(purchaseRepository.findForUpdateById(purchase.getId()))
                .thenReturn(Optional.of(purchase));

        assertThrows(
                InvalidInputException.class, () -> service.confirmPayment(purchase.getId(), "x"));
    }

    @Test
    void shouldRefundAPaymentForARefundedPurchase() {
        stubLockedPurchase();
        purchase.setStatus(PurchaseStatus.REFUNDED);

        assertEquals(
                Settlement.REFUND, service.settle(purchase.getId(), "987", BigDecimal.TEN, "ARS"));
        assertEquals(RefundReason.PURCHASE_CANCELLED, recordedReason());
    }

    @Test
    void shouldRecordAWithdrawalAsAPendingRefundOfTheWholePayment() {
        Instant now = Instant.now();
        paidWithMercadoPago(now.plus(Duration.ofDays(3)));
        when(refundRepository.findByPaymentId("987")).thenReturn(Optional.empty());
        User actor = actor();

        String paymentId =
                service.startWithdrawal(
                        organization.getId(), purchase.getId(), actor, "  Nos equivocamos  ", now);

        assertEquals("987", paymentId);
        ArgumentCaptor<PurchaseRefund> refund = ArgumentCaptor.forClass(PurchaseRefund.class);
        verify(refundRepository).save(refund.capture());
        assertEquals(RefundReason.BUYER_REQUESTED, refund.getValue().getReason());
        assertEquals(RefundStatus.PENDING, refund.getValue().getStatus());
        assertEquals(BigDecimal.TEN, refund.getValue().getAmount());
        assertEquals("Nos equivocamos", refund.getValue().getNote());
        assertSame(actor, refund.getValue().getRequestedBy());
        assertEquals(PurchaseStatus.PAID, purchase.getStatus());
    }

    @Test
    void shouldStoreNoNoteWhenTheReasonIsBlank() {
        Instant now = Instant.now();
        paidWithMercadoPago(now.plus(Duration.ofDays(3)));
        when(refundRepository.findByPaymentId("987")).thenReturn(Optional.empty());

        service.startWithdrawal(organization.getId(), purchase.getId(), actor(), " ", now);

        ArgumentCaptor<PurchaseRefund> refund = ArgumentCaptor.forClass(PurchaseRefund.class);
        verify(refundRepository).save(refund.capture());
        assertNull(refund.getValue().getNote());
    }

    @Test
    void shouldRejectAWithdrawalAfterThePeriod() {
        Instant now = Instant.now();
        paidWithMercadoPago(now);
        when(refundRepository.findByPaymentId("987")).thenReturn(Optional.empty());

        assertThrows(
                InvalidInputException.class,
                () ->
                        service.startWithdrawal(
                                organization.getId(), purchase.getId(), actor(), null, now));
        verify(refundRepository, never()).save(any());
    }

    @Test
    void shouldRejectAWithdrawalOfAnUnpaidOrHandConfirmedPurchase() {
        Instant now = Instant.now();
        when(purchaseRepository.findForUpdateById(purchase.getId()))
                .thenReturn(Optional.of(purchase));
        assertThrows(
                InvalidInputException.class,
                () ->
                        service.startWithdrawal(
                                organization.getId(), purchase.getId(), actor(), null, now));

        purchase.setStatus(PurchaseStatus.PAID);
        purchase.setPaymentReference("TRANSFERENCIA-12");
        assertThrows(
                InvalidInputException.class,
                () ->
                        service.startWithdrawal(
                                organization.getId(), purchase.getId(), actor(), null, now));
        verify(refundRepository, never()).save(any());
    }

    @Test
    void shouldNotWithdrawAPurchaseOfAnotherOrganization() {
        paidWithMercadoPago(Instant.now().plus(Duration.ofDays(3)));

        assertThrows(
                NotFoundException.class,
                () ->
                        service.startWithdrawal(
                                UUID.randomUUID(), purchase.getId(), actor(), null, Instant.now()));
    }

    @Test
    void shouldResumeAWithdrawalAlreadyStartedEvenAfterThePeriod() {
        Instant now = Instant.now();
        paidWithMercadoPago(now.minus(Duration.ofDays(1)));
        PurchaseRefund started =
                PurchaseRefund.builder()
                        .paymentId("987")
                        .reason(RefundReason.BUYER_REQUESTED)
                        .build();
        when(refundRepository.findByPaymentId("987")).thenReturn(Optional.of(started));

        assertEquals(
                "987",
                service.startWithdrawal(
                        organization.getId(), purchase.getId(), actor(), null, now));
        verify(refundRepository, never()).save(any());
    }

    @Test
    void shouldRevokeThePurchaseWhenItsWithdrawalRefundIsDone() {
        purchase.setStatus(PurchaseStatus.PAID);
        PurchaseRefund pending =
                PurchaseRefund.builder()
                        .paymentId("987")
                        .reason(RefundReason.BUYER_REQUESTED)
                        .amount(BigDecimal.TEN)
                        .purchase(purchase)
                        .build();
        when(refundRepository.findByPaymentId("987")).thenReturn(Optional.of(pending));
        when(purchaseRepository.findForUpdateById(purchase.getId()))
                .thenReturn(Optional.of(purchase));

        service.markRefunded("987", "r-1");

        assertEquals(RefundStatus.REFUNDED, pending.getStatus());
        verify(revocationService)
                .revoke(eq(purchase), eq(RefundReason.BUYER_REQUESTED), eq(BigDecimal.TEN), any());
    }

    @Test
    void shouldNotRevokeTwiceOrRevokeForAPaymentThatNeverPaidThePurchase() {
        purchase.setStatus(PurchaseStatus.REFUNDED);
        PurchaseRefund withdrawal =
                PurchaseRefund.builder()
                        .paymentId("987")
                        .reason(RefundReason.BUYER_REQUESTED)
                        .purchase(purchase)
                        .build();
        PurchaseRefund duplicate =
                PurchaseRefund.builder()
                        .paymentId("555")
                        .reason(RefundReason.ALREADY_PAID)
                        .purchase(purchase)
                        .build();
        when(refundRepository.findByPaymentId("987")).thenReturn(Optional.of(withdrawal));
        when(refundRepository.findByPaymentId("555")).thenReturn(Optional.of(duplicate));
        when(purchaseRepository.findForUpdateById(purchase.getId()))
                .thenReturn(Optional.of(purchase));

        service.markRefunded("987", null);
        service.markRefunded("555", null);

        verifyNoInteractions(revocationService);
    }

    @Test
    void shouldCompleteARefundSignaStartedWhenMercadoPagoReportsIt() {
        purchase.setStatus(PurchaseStatus.PAID);
        PurchaseRefund pending =
                PurchaseRefund.builder()
                        .paymentId("987")
                        .reason(RefundReason.BUYER_REQUESTED)
                        .amount(BigDecimal.TEN)
                        .purchase(purchase)
                        .build();
        when(refundRepository.findByPaymentId("987")).thenReturn(Optional.of(pending));
        when(purchaseRepository.findForUpdateById(purchase.getId()))
                .thenReturn(Optional.of(purchase));

        assertEquals(Reversal.REFUND_COMPLETED, service.reverse("987", false, BigDecimal.TEN));
        assertEquals(RefundStatus.REFUNDED, pending.getStatus());
        verify(revocationService).revoke(eq(purchase), any(), any(), any());

        assertEquals(Reversal.IGNORED, service.reverse("987", false, BigDecimal.TEN));
    }

    @Test
    void shouldRevokeAPurchaseWhoseMoneyWasTakenBackByAChargeback() {
        purchase.setStatus(PurchaseStatus.PAID);
        purchase.setPaymentReference("mercadopago:987");
        when(refundRepository.findByPaymentId("987")).thenReturn(Optional.empty());
        when(purchaseRepository.findForUpdateByPaymentReference("mercadopago:987"))
                .thenReturn(Optional.of(purchase));

        assertEquals(Reversal.PURCHASE_REVOKED, service.reverse("987", true, BigDecimal.TEN));

        ArgumentCaptor<PurchaseRefund> refund = ArgumentCaptor.forClass(PurchaseRefund.class);
        verify(refundRepository).save(refund.capture());
        assertEquals(RefundReason.CHARGEBACK, refund.getValue().getReason());
        assertEquals(RefundStatus.REFUNDED, refund.getValue().getStatus());
        assertNotNull(refund.getValue().getRefundedAt());
        verify(revocationService)
                .revoke(eq(purchase), eq(RefundReason.CHARGEBACK), eq(BigDecimal.TEN), any());
    }

    @Test
    void shouldRevokeAPurchaseRefundedFromMercadoPagoWithItsTotalWhenNoAmountIsGiven() {
        purchase.setStatus(PurchaseStatus.PAID);
        when(refundRepository.findByPaymentId("987")).thenReturn(Optional.empty());
        when(purchaseRepository.findForUpdateByPaymentReference("mercadopago:987"))
                .thenReturn(Optional.of(purchase));

        service.reverse("987", false, null);

        verify(revocationService)
                .revoke(eq(purchase), eq(RefundReason.EXTERNAL_REFUND), eq(BigDecimal.TEN), any());
    }

    @Test
    void shouldIgnoreAReversalOfAPaymentThatPaidNothing() {
        when(refundRepository.findByPaymentId("987")).thenReturn(Optional.empty());
        when(purchaseRepository.findForUpdateByPaymentReference("mercadopago:987"))
                .thenReturn(Optional.empty());

        assertEquals(Reversal.IGNORED, service.reverse("987", true, BigDecimal.TEN));
        verify(refundRepository, never()).save(any());
        verifyNoInteractions(revocationService);
    }

    @Test
    void shouldFindOnlyAPaidPurchaseByItsPayment() {
        purchase.setStatus(PurchaseStatus.PAID);
        when(purchaseRepository.findByPaymentReference("mercadopago:987"))
                .thenReturn(Optional.of(purchase));
        assertEquals(Optional.of(purchase), service.findPaidBy("987"));

        purchase.setStatus(PurchaseStatus.REFUNDED);
        assertTrue(service.findPaidBy("987").isEmpty());
    }
}
