package com.signasource.signa_api.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.admin.dto.AdminPurchaseDetailResponse;
import com.signasource.signa_api.admin.dto.AdminPurchaseListItemResponse;
import com.signasource.signa_api.admin.dto.ConfirmPaymentRequest;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.entity.GemOrder;
import com.signasource.signa_api.gamification.entity.GemOrderStatus;
import com.signasource.signa_api.gamification.entity.GemPack;
import com.signasource.signa_api.gamification.repository.GemOrderRepository;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseOrder;
import com.signasource.signa_api.learning.entity.CourseOrderStatus;
import com.signasource.signa_api.learning.repository.CourseOrderRepository;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.entity.RefundRequest;
import com.signasource.signa_api.organizations.entity.RefundRequestStatus;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.repository.RefundRequestRepository;
import com.signasource.signa_api.organizations.service.PurchasePaymentService;
import com.signasource.signa_api.users.entity.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminPurchasesServiceTest {

    @Mock private CoursePurchaseRepository seatsPurchaseRepository;
    @Mock private GemOrderRepository gemOrderRepository;
    @Mock private CourseOrderRepository courseOrderRepository;
    @Mock private RefundRequestRepository refundRequestRepository;
    @Mock private AdminRefundRequestService refundRequestService;
    @Mock private PurchasePaymentService paymentService;

    @InjectMocks private AdminPurchasesService service;

    // ── listAll ───────────────────────────────────────────────────────────────

    @Test
    void listAll_returnsEmptyListWhenNoPurchases() {
        when(refundRequestRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(seatsPurchaseRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(gemOrderRepository.findAll()).thenReturn(List.of());
        when(courseOrderRepository.findAll()).thenReturn(List.of());

        List<AdminPurchaseListItemResponse> result = service.listAll();

        assertEquals(0, result.size());
    }

    @Test
    void listAll_includesSeatsGemAndCourseItems() {
        CoursePurchase seat = sampleSeatsPurchase();
        GemOrder gem = sampleGemOrder();
        CourseOrder course = sampleCourseOrder();
        when(refundRequestRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(seatsPurchaseRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(seat));
        when(gemOrderRepository.findAll()).thenReturn(List.of(gem));
        when(courseOrderRepository.findAll()).thenReturn(List.of(course));

        List<AdminPurchaseListItemResponse> result = service.listAll();

        assertEquals(3, result.size());
    }

    @Test
    void listAll_flagsPendingRefundRequests() {
        UUID purchaseId = UUID.randomUUID();
        CoursePurchase purchase = sampleSeatsPurchase();
        when(purchase.getId()).thenReturn(purchaseId);
        RefundRequest pending = mock(RefundRequest.class);
        when(pending.getStatus()).thenReturn(RefundRequestStatus.PENDING);
        when(pending.getPurchaseType()).thenReturn(AdminPurchasesService.TYPE_SEATS);
        when(pending.getPurchaseId()).thenReturn(purchaseId);
        when(refundRequestRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(pending));
        when(seatsPurchaseRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(purchase));
        when(gemOrderRepository.findAll()).thenReturn(List.of());
        when(courseOrderRepository.findAll()).thenReturn(List.of());

        List<AdminPurchaseListItemResponse> result = service.listAll();

        assertEquals(1, result.size());
        assertTrue(result.get(0).pendingRefundRequest());
    }

    // ── detail ────────────────────────────────────────────────────────────────

    @Test
    void detail_returnsSeatsDetail() {
        UUID id = UUID.randomUUID();
        CoursePurchase p = sampleSeatsPurchase();
        when(seatsPurchaseRepository.findForUpdateById(id)).thenReturn(Optional.of(p));
        when(refundRequestService.listForPurchase(AdminPurchasesService.TYPE_SEATS, id))
                .thenReturn(List.of());

        AdminPurchaseDetailResponse result = service.detail(id, AdminPurchasesService.TYPE_SEATS);

        assertNotNull(result);
        assertEquals(AdminPurchasesService.TYPE_SEATS, result.type());
    }

    @Test
    void detail_returnsGemsDetail() {
        UUID id = UUID.randomUUID();
        GemOrder o = sampleGemOrder();
        when(o.getId()).thenReturn(id);
        when(gemOrderRepository.findById(id)).thenReturn(Optional.of(o));
        when(refundRequestService.listForPurchase(AdminPurchasesService.TYPE_GEMS, id))
                .thenReturn(List.of());

        AdminPurchaseDetailResponse result = service.detail(id, AdminPurchasesService.TYPE_GEMS);

        assertNotNull(result);
        assertEquals(AdminPurchasesService.TYPE_GEMS, result.type());
    }

    @Test
    void detail_returnsCourseOrderDetail() {
        UUID id = UUID.randomUUID();
        CourseOrder o = sampleCourseOrder();
        when(courseOrderRepository.findById(id)).thenReturn(Optional.of(o));
        when(refundRequestService.listForPurchase(AdminPurchasesService.TYPE_COURSE, id))
                .thenReturn(List.of());

        AdminPurchaseDetailResponse result = service.detail(id, AdminPurchasesService.TYPE_COURSE);

        assertNotNull(result);
        assertEquals(AdminPurchasesService.TYPE_COURSE, result.type());
    }

    @Test
    void detail_throwsForUnknownType() {
        assertThrows(NotFoundException.class, () -> service.detail(UUID.randomUUID(), "UNKNOWN"));
    }

    @Test
    void detail_throwsWhenSeatsNotFound() {
        UUID id = UUID.randomUUID();
        when(seatsPurchaseRepository.findForUpdateById(id)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> service.detail(id, AdminPurchasesService.TYPE_SEATS));
    }

    @Test
    void detail_throwsWhenGemOrderNotFound() {
        UUID id = UUID.randomUUID();
        when(gemOrderRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class, () -> service.detail(id, AdminPurchasesService.TYPE_GEMS));
    }

    // ── confirmPayment ────────────────────────────────────────────────────────

    @Test
    void confirmPayment_delegatesToPaymentServiceAndReturnsDetail() {
        UUID purchaseId = UUID.randomUUID();
        ConfirmPaymentRequest req = new ConfirmPaymentRequest("ref-123", null, null);
        CoursePurchase p = sampleSeatsPurchase();
        when(seatsPurchaseRepository.findForUpdateById(purchaseId)).thenReturn(Optional.of(p));
        when(refundRequestService.listForPurchase(AdminPurchasesService.TYPE_SEATS, purchaseId))
                .thenReturn(List.of());

        AdminPurchaseDetailResponse result = service.confirmPayment(purchaseId, req, "Admin User");

        verify(paymentService).confirmPaymentFull(purchaseId, req, "Admin User");
        assertNotNull(result);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private CoursePurchase sampleSeatsPurchase() {
        Organization org = mock(Organization.class);
        when(org.getId()).thenReturn(UUID.randomUUID());
        when(org.getName()).thenReturn("Acme Corp");
        when(org.getContactEmail()).thenReturn("contact@acme.com");
        Course course = mock(Course.class);
        when(course.getName()).thenReturn("LSA básico");
        CoursePurchase p = mock(CoursePurchase.class);
        when(p.getId()).thenReturn(UUID.randomUUID());
        when(p.getOrganization()).thenReturn(org);
        when(p.getCourse()).thenReturn(course);
        when(p.getStatus()).thenReturn(PurchaseStatus.PAID);
        when(p.getSeats()).thenReturn(10);
        when(p.getDurationDays()).thenReturn(365);
        when(p.getTotalAmount()).thenReturn(BigDecimal.valueOf(5000));
        when(p.getCurrency()).thenReturn("ARS");
        when(p.getCreatedAt()).thenReturn(Instant.now());
        when(p.getPaymentReference()).thenReturn(null);
        when(p.getPaymentMethod()).thenReturn(null);
        when(p.getCreatedBy()).thenReturn(null);
        when(p.getPaidAt()).thenReturn(null);
        when(p.getRefundedAt()).thenReturn(null);
        when(p.getConfirmedByName()).thenReturn(null);
        when(p.getConfirmedAt()).thenReturn(null);
        when(p.getDiscountPercent()).thenReturn(0);
        return p;
    }

    private GemOrder sampleGemOrder() {
        User user = mock(User.class);
        when(user.getName()).thenReturn("Juan");
        when(user.getLastName()).thenReturn("Pérez");
        when(user.getUsername()).thenReturn("juanperez");
        when(user.getEmail()).thenReturn("juan@example.com");
        GemPack pack = mock(GemPack.class);
        when(pack.getGems()).thenReturn(300);
        when(pack.getProductId()).thenReturn("pack-300");
        GemOrder o = mock(GemOrder.class);
        when(o.getId()).thenReturn(UUID.randomUUID());
        when(o.getUser()).thenReturn(user);
        when(o.getGemPack()).thenReturn(pack);
        when(o.getGems()).thenReturn(300);
        when(o.getAmount()).thenReturn(BigDecimal.valueOf(500));
        when(o.getCurrency()).thenReturn("ARS");
        when(o.getStatus()).thenReturn(GemOrderStatus.PAID);
        when(o.getCreatedAt()).thenReturn(Instant.now());
        when(o.getMercadoPagoOrderId()).thenReturn(null);
        when(o.getPaidAt()).thenReturn(null);
        when(o.getRefundedAt()).thenReturn(null);
        return o;
    }

    private CourseOrder sampleCourseOrder() {
        User user = mock(User.class);
        when(user.getName()).thenReturn("Ana");
        when(user.getLastName()).thenReturn("García");
        when(user.getUsername()).thenReturn("anagarcia");
        when(user.getEmail()).thenReturn("ana@example.com");
        Course course = mock(Course.class);
        when(course.getName()).thenReturn("LSA avanzado");
        CourseOrder o = mock(CourseOrder.class);
        when(o.getId()).thenReturn(UUID.randomUUID());
        when(o.getUser()).thenReturn(user);
        when(o.getCourse()).thenReturn(course);
        when(o.getAmount()).thenReturn(BigDecimal.valueOf(2000));
        when(o.getCurrency()).thenReturn("ARS");
        when(o.getStatus()).thenReturn(CourseOrderStatus.PAID);
        when(o.getCreatedAt()).thenReturn(Instant.now());
        when(o.getMercadoPagoOrderId()).thenReturn(null);
        when(o.getPaidAt()).thenReturn(null);
        when(o.getRefundedAt()).thenReturn(null);
        return o;
    }
}
