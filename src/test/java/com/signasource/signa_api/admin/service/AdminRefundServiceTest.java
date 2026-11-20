package com.signasource.signa_api.admin.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.PaymentProviderException;
import com.signasource.signa_api.gamification.entity.GemOrder;
import com.signasource.signa_api.gamification.entity.GemOrderStatus;
import com.signasource.signa_api.gamification.repository.GemOrderRepository;
import com.signasource.signa_api.gamification.service.GemOrderSettlementService;
import com.signasource.signa_api.learning.entity.CourseOrder;
import com.signasource.signa_api.learning.entity.CourseOrderStatus;
import com.signasource.signa_api.learning.repository.CourseOrderRepository;
import com.signasource.signa_api.learning.service.CourseOrderSettlementService;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.service.MercadoPagoClient;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.Payment;
import com.signasource.signa_api.organizations.service.PurchasePaymentService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminRefundServiceTest {

    @Mock private CoursePurchaseRepository seatsPurchaseRepository;
    @Mock private GemOrderRepository gemOrderRepository;
    @Mock private CourseOrderRepository courseOrderRepository;
    @Mock private PurchasePaymentService purchasePaymentService;
    @Mock private GemOrderSettlementService gemOrderSettlementService;
    @Mock private CourseOrderSettlementService courseOrderSettlementService;
    @Mock private MercadoPagoClient mercadoPagoClient;

    @InjectMocks private AdminRefundService service;

    // ── seats ─────────────────────────────────────────────────────────────────

    @Test
    void shouldRefundInMercadoPagoBeforeRevokingSeats() {
        UUID id = UUID.randomUUID();
        when(purchasePaymentService.startAdminRefund(id)).thenReturn("mp-1");
        when(mercadoPagoClient.refund("mp-1")).thenReturn("refund-1");

        service.refund(AdminPurchasesService.TYPE_SEATS, id, false);

        InOrder order = inOrder(purchasePaymentService, mercadoPagoClient);
        order.verify(purchasePaymentService).startAdminRefund(id);
        order.verify(mercadoPagoClient).refund("mp-1");
        order.verify(purchasePaymentService).markRefunded("mp-1", "refund-1");
    }

    @Test
    void shouldReverseSeatsManuallyWhenNotPaidWithMercadoPago() {
        UUID id = UUID.randomUUID();
        when(purchasePaymentService.startAdminRefund(id)).thenReturn(null);

        service.refund(AdminPurchasesService.TYPE_SEATS, id, false);

        verify(purchasePaymentService).reverseManual(id);
        verifyNoInteractions(mercadoPagoClient);
    }

    @Test
    void shouldNotRevokeSeatsWhenMercadoPagoFails() {
        UUID id = UUID.randomUUID();
        when(purchasePaymentService.startAdminRefund(id)).thenReturn("mp-1");
        when(mercadoPagoClient.refund("mp-1")).thenThrow(new PaymentProviderException("down"));
        Payment p = payment(false);
        when(mercadoPagoClient.getOrder("mp-1")).thenReturn(p);

        assertThrows(
                PaymentProviderException.class,
                () -> service.refund(AdminPurchasesService.TYPE_SEATS, id, false));

        verify(purchasePaymentService, never()).markRefunded(any(), any());
    }

    @Test
    void shouldCompleteSeatsWhenMercadoPagoWasAlreadyRefunded() {
        UUID id = UUID.randomUUID();
        when(purchasePaymentService.startAdminRefund(id)).thenReturn("mp-1");
        when(mercadoPagoClient.refund("mp-1")).thenThrow(new PaymentProviderException("done"));
        Payment p = payment(true);
        when(mercadoPagoClient.getOrder("mp-1")).thenReturn(p);

        service.refund(AdminPurchasesService.TYPE_SEATS, id, false);

        verify(purchasePaymentService).markRefunded("mp-1", null);
    }

    @Test
    void shouldSkipUnpaidSeatsWhenLenient() {
        UUID id = UUID.randomUUID();
        CoursePurchase purchase = mock(CoursePurchase.class);
        when(purchase.getStatus()).thenReturn(PurchaseStatus.REFUNDED);
        when(seatsPurchaseRepository.findById(id)).thenReturn(Optional.of(purchase));

        service.refund(AdminPurchasesService.TYPE_SEATS, id, true);

        verifyNoInteractions(purchasePaymentService, mercadoPagoClient);
    }

    @Test
    void shouldRefundPaidSeatsWhenLenient() {
        UUID id = UUID.randomUUID();
        CoursePurchase purchase = mock(CoursePurchase.class);
        when(purchase.getStatus()).thenReturn(PurchaseStatus.PAID);
        when(seatsPurchaseRepository.findById(id)).thenReturn(Optional.of(purchase));
        when(purchasePaymentService.startAdminRefund(id)).thenReturn("mp-1");

        service.refund(AdminPurchasesService.TYPE_SEATS, id, true);

        verify(purchasePaymentService).markRefunded("mp-1", null);
    }

    // ── gems ──────────────────────────────────────────────────────────────────

    @Test
    void shouldRefundInMercadoPagoBeforeReversingGems() {
        UUID id = UUID.randomUUID();
        stubGemOrder(id, GemOrderStatus.PAID, "mp-gem");

        service.refund(AdminPurchasesService.TYPE_GEMS, id, false);

        InOrder order = inOrder(mercadoPagoClient, gemOrderSettlementService);
        order.verify(mercadoPagoClient).refund("mp-gem");
        order.verify(gemOrderSettlementService).reverseManual(id);
    }

    @Test
    void shouldNotReverseGemsWhenMercadoPagoFails() {
        UUID id = UUID.randomUUID();
        stubGemOrder(id, GemOrderStatus.PAID, "mp-gem");
        when(mercadoPagoClient.refund("mp-gem")).thenThrow(new PaymentProviderException("down"));
        Payment p = payment(false);
        when(mercadoPagoClient.getOrder("mp-gem")).thenReturn(p);

        assertThrows(
                PaymentProviderException.class,
                () -> service.refund(AdminPurchasesService.TYPE_GEMS, id, false));

        verify(gemOrderSettlementService, never()).reverseManual(any());
    }

    @Test
    void shouldReverseGemsWithoutMercadoPagoWhenNoPayment() {
        UUID id = UUID.randomUUID();
        stubGemOrder(id, GemOrderStatus.PAID, null);

        service.refund(AdminPurchasesService.TYPE_GEMS, id, false);

        verify(gemOrderSettlementService).reverseManual(id);
        verifyNoInteractions(mercadoPagoClient);
    }

    @Test
    void shouldRejectGemsThatAreNotPaid() {
        UUID id = UUID.randomUUID();
        stubGemOrder(id, GemOrderStatus.REFUNDED, "mp-gem");

        assertThrows(
                InvalidInputException.class,
                () -> service.refund(AdminPurchasesService.TYPE_GEMS, id, false));
        verifyNoInteractions(mercadoPagoClient);
    }

    @Test
    void shouldSkipGemsThatAreNotPaidWhenLenient() {
        UUID id = UUID.randomUUID();
        stubGemOrder(id, GemOrderStatus.REFUNDED, "mp-gem");

        service.refund(AdminPurchasesService.TYPE_GEMS, id, true);

        verifyNoInteractions(mercadoPagoClient, gemOrderSettlementService);
    }

    @Test
    void shouldFailOnMissingGemOrderUnlessLenient() {
        UUID id = UUID.randomUUID();
        when(gemOrderRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> service.refund(AdminPurchasesService.TYPE_GEMS, id, false));
        service.refund(AdminPurchasesService.TYPE_GEMS, id, true);
        verifyNoInteractions(gemOrderSettlementService);
    }

    // ── courses ───────────────────────────────────────────────────────────────

    @Test
    void shouldRefundInMercadoPagoBeforeReversingCourse() {
        UUID id = UUID.randomUUID();
        stubCourseOrder(id, CourseOrderStatus.PAID, "mp-course");

        service.refund(AdminPurchasesService.TYPE_COURSE, id, false);

        InOrder order = inOrder(mercadoPagoClient, courseOrderSettlementService);
        order.verify(mercadoPagoClient).refund("mp-course");
        order.verify(courseOrderSettlementService).reverseManual(id);
    }

    @Test
    void shouldRejectCourseOrdersThatAreNotPaid() {
        UUID id = UUID.randomUUID();
        stubCourseOrder(id, CourseOrderStatus.PENDING, null);

        assertThrows(
                InvalidInputException.class,
                () -> service.refund(AdminPurchasesService.TYPE_COURSE, id, false));
        service.refund(AdminPurchasesService.TYPE_COURSE, id, true);
        verifyNoInteractions(courseOrderSettlementService);
    }

    @Test
    void shouldFailOnMissingCourseOrderUnlessLenient() {
        UUID id = UUID.randomUUID();
        when(courseOrderRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> service.refund(AdminPurchasesService.TYPE_COURSE, id, false));
        service.refund(AdminPurchasesService.TYPE_COURSE, id, true);
        verifyNoInteractions(courseOrderSettlementService);
    }

    @Test
    void shouldThrowNotFoundForUnknownType() {
        assertThrows(
                NotFoundException.class, () -> service.refund("UNKNOWN", UUID.randomUUID(), false));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void stubGemOrder(UUID id, GemOrderStatus status, String mpOrderId) {
        GemOrder order = mock(GemOrder.class);
        when(order.getStatus()).thenReturn(status);
        when(order.getMercadoPagoOrderId()).thenReturn(mpOrderId);
        when(gemOrderRepository.findById(id)).thenReturn(Optional.of(order));
    }

    private void stubCourseOrder(UUID id, CourseOrderStatus status, String mpOrderId) {
        CourseOrder order = mock(CourseOrder.class);
        when(order.getStatus()).thenReturn(status);
        when(order.getMercadoPagoOrderId()).thenReturn(mpOrderId);
        when(courseOrderRepository.findById(id)).thenReturn(Optional.of(order));
    }

    private static Payment payment(boolean refunded) {
        Payment payment = mock(Payment.class);
        when(payment.isRefunded()).thenReturn(refunded);
        return payment;
    }
}
