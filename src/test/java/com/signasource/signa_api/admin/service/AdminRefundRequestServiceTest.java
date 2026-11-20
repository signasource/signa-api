package com.signasource.signa_api.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.admin.dto.AdminRefundRequestResponse;
import com.signasource.signa_api.admin.dto.UpdateRefundRequestRequest;
import com.signasource.signa_api.exceptions.InvalidInputException;
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
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.entity.RefundRequest;
import com.signasource.signa_api.organizations.entity.RefundRequestReason;
import com.signasource.signa_api.organizations.entity.RefundRequestStatus;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.repository.RefundRequestRepository;
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
class AdminRefundRequestServiceTest {

    @Mock private RefundRequestRepository repository;
    @Mock private CoursePurchaseRepository seatsPurchaseRepository;
    @Mock private GemOrderRepository gemOrderRepository;
    @Mock private CourseOrderRepository courseOrderRepository;

    @InjectMocks private AdminRefundRequestService service;

    // ── listAll ───────────────────────────────────────────────────────────────

    @Test
    void listAll_returnsEmptyList() {
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

        List<AdminRefundRequestResponse> result = service.listAll();

        assertEquals(0, result.size());
    }

    @Test
    void listAll_returnsMappedItems() {
        UUID purchaseId = UUID.randomUUID();
        stubSeatsPurchase(purchaseId);
        RefundRequest r = sampleRequest(AdminPurchasesService.TYPE_SEATS, purchaseId);
        when(repository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(r));

        List<AdminRefundRequestResponse> result = service.listAll();

        assertEquals(1, result.size());
        assertEquals(AdminPurchasesService.TYPE_SEATS, result.get(0).purchaseType());
    }

    // ── listForPurchase ───────────────────────────────────────────────────────

    @Test
    void listForPurchase_delegatesToRepository() {
        UUID purchaseId = UUID.randomUUID();
        stubSeatsPurchase(purchaseId);
        RefundRequest r = sampleRequest(AdminPurchasesService.TYPE_SEATS, purchaseId);
        when(repository.findByPurchaseTypeAndPurchaseId(
                        AdminPurchasesService.TYPE_SEATS, purchaseId))
                .thenReturn(List.of(r));

        List<AdminRefundRequestResponse> result =
                service.listForPurchase(AdminPurchasesService.TYPE_SEATS, purchaseId);

        assertEquals(1, result.size());
    }

    // ── get ───────────────────────────────────────────────────────────────────

    @Test
    void get_returnsResponseWhenFound() {
        UUID id = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        stubSeatsPurchase(purchaseId);
        RefundRequest r = sampleRequest(AdminPurchasesService.TYPE_SEATS, purchaseId);
        r.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(r));

        AdminRefundRequestResponse result = service.get(id);

        assertNotNull(result);
        assertEquals(id, result.id());
    }

    @Test
    void get_throwsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.get(id));
    }

    // ── update – RESOLVE ──────────────────────────────────────────────────────

    @Test
    void update_resolvesPendingRequest() {
        UUID id = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        stubSeatsPurchase(purchaseId);
        RefundRequest r = sampleRequest(AdminPurchasesService.TYPE_SEATS, purchaseId);
        r.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(r));

        AdminRefundRequestResponse result =
                service.update(id, new UpdateRefundRequestRequest("RESOLVE", null), "Admin Test");

        assertEquals("RESOLVED", result.status());
        assertEquals("Admin Test", result.resolvedByName());
        assertNotNull(result.resolvedAt());
    }

    @Test
    void update_resolve_throwsWhenAlreadyResolved() {
        UUID id = UUID.randomUUID();
        RefundRequest r = sampleRequest(AdminPurchasesService.TYPE_SEATS, UUID.randomUUID());
        r.setId(id);
        r.setStatus(RefundRequestStatus.RESOLVED);
        when(repository.findById(id)).thenReturn(Optional.of(r));

        assertThrows(
                InvalidInputException.class,
                () ->
                        service.update(
                                id, new UpdateRefundRequestRequest("RESOLVE", null), "Admin Test"));
    }

    // ── update – REOPEN ───────────────────────────────────────────────────────

    @Test
    void update_reopensResolvedRequest() {
        UUID id = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        stubSeatsPurchase(purchaseId);
        RefundRequest r = sampleRequest(AdminPurchasesService.TYPE_SEATS, purchaseId);
        r.setId(id);
        r.setStatus(RefundRequestStatus.RESOLVED);
        r.setResolvedByName("Previous Admin");
        r.setResolvedAt(Instant.now());
        when(repository.findById(id)).thenReturn(Optional.of(r));

        AdminRefundRequestResponse result =
                service.update(id, new UpdateRefundRequestRequest("REOPEN", null), "Admin Test");

        assertEquals("PENDING", result.status());
        assertNull(result.resolvedByName());
        assertNull(result.resolvedAt());
    }

    @Test
    void update_reopen_throwsWhenStillPending() {
        UUID id = UUID.randomUUID();
        RefundRequest r = sampleRequest(AdminPurchasesService.TYPE_SEATS, UUID.randomUUID());
        r.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(r));

        assertThrows(
                InvalidInputException.class,
                () ->
                        service.update(
                                id, new UpdateRefundRequestRequest("REOPEN", null), "Admin Test"));
    }

    // ── update – note ─────────────────────────────────────────────────────────

    @Test
    void update_setsNoteWhenProvided() {
        UUID id = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        stubSeatsPurchase(purchaseId);
        RefundRequest r = sampleRequest(AdminPurchasesService.TYPE_SEATS, purchaseId);
        r.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(r));

        AdminRefundRequestResponse result =
                service.update(id, new UpdateRefundRequestRequest(null, "nota de admin"), "Admin");

        assertEquals("nota de admin", result.note());
    }

    @Test
    void update_setsNoteToNullWhenBlank() {
        UUID id = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        stubSeatsPurchase(purchaseId);
        RefundRequest r = sampleRequest(AdminPurchasesService.TYPE_SEATS, purchaseId);
        r.setId(id);
        r.setNote("old note");
        when(repository.findById(id)).thenReturn(Optional.of(r));

        AdminRefundRequestResponse result =
                service.update(id, new UpdateRefundRequestRequest(null, "   "), "Admin");

        assertNull(result.note());
    }

    // ── update – unknown action ───────────────────────────────────────────────

    @Test
    void update_throwsForUnknownAction() {
        UUID id = UUID.randomUUID();
        RefundRequest r = sampleRequest(AdminPurchasesService.TYPE_SEATS, UUID.randomUUID());
        r.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(r));

        assertThrows(
                InvalidInputException.class,
                () -> service.update(id, new UpdateRefundRequestRequest("UNKNOWN", null), "Admin"));
    }

    // ── loadSummary – gem and course types ───────────────────────────────────

    @Test
    void get_mapsGemsTypeCorrectly() {
        UUID id = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        GemPack pack = mock(GemPack.class);
        when(pack.getProductId()).thenReturn("pack-300");
        GemOrder o = mock(GemOrder.class);
        when(o.getGemPack()).thenReturn(pack);
        when(o.getGems()).thenReturn(300);
        when(o.getAmount()).thenReturn(BigDecimal.valueOf(500));
        when(o.getCurrency()).thenReturn("ARS");
        when(o.getStatus()).thenReturn(GemOrderStatus.PAID);
        when(o.getPaidAt()).thenReturn(null);
        when(gemOrderRepository.findById(purchaseId)).thenReturn(Optional.of(o));
        RefundRequest r = sampleRequest(AdminPurchasesService.TYPE_GEMS, purchaseId);
        r.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(r));

        AdminRefundRequestResponse result = service.get(id);

        assertEquals(AdminPurchasesService.TYPE_GEMS, result.purchaseType());
        assertEquals("pack-300", result.productName());
    }

    @Test
    void get_mapsCourseTypeCorrectly() {
        UUID id = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        Course course = mock(Course.class);
        when(course.getName()).thenReturn("LSA avanzado");
        CourseOrder o = mock(CourseOrder.class);
        when(o.getCourse()).thenReturn(course);
        when(o.getAmount()).thenReturn(BigDecimal.valueOf(2000));
        when(o.getCurrency()).thenReturn("ARS");
        when(o.getStatus()).thenReturn(CourseOrderStatus.PAID);
        when(o.getPaidAt()).thenReturn(null);
        when(courseOrderRepository.findById(purchaseId)).thenReturn(Optional.of(o));
        RefundRequest r = sampleRequest(AdminPurchasesService.TYPE_COURSE, purchaseId);
        r.setId(id);
        when(repository.findById(id)).thenReturn(Optional.of(r));

        AdminRefundRequestResponse result = service.get(id);

        assertEquals(AdminPurchasesService.TYPE_COURSE, result.purchaseType());
        assertEquals("LSA avanzado", result.productName());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private RefundRequest sampleRequest(String type, UUID purchaseId) {
        User requester =
                User.builder()
                        .id(UUID.randomUUID())
                        .name("María")
                        .lastName("López")
                        .username("marialopez")
                        .email("maria@example.com")
                        .build();
        return RefundRequest.builder()
                .purchaseType(type)
                .purchaseId(purchaseId)
                .requestedBy(requester)
                .reason(RefundRequestReason.MISTAKE)
                .channel("web")
                .message("Pido reembolso por error")
                .status(RefundRequestStatus.PENDING)
                .build();
    }

    private void stubSeatsPurchase(UUID purchaseId) {
        Course course = mock(Course.class);
        when(course.getName()).thenReturn("LSA básico");
        CoursePurchase p = mock(CoursePurchase.class);
        when(p.getCourse()).thenReturn(course);
        when(p.getSeats()).thenReturn(10);
        when(p.getDurationDays()).thenReturn(365);
        when(p.getTotalAmount()).thenReturn(BigDecimal.valueOf(5000));
        when(p.getCurrency()).thenReturn("ARS");
        when(p.getStatus()).thenReturn(PurchaseStatus.PAID);
        when(p.getPaidAt()).thenReturn(null);
        when(seatsPurchaseRepository.findById(purchaseId)).thenReturn(Optional.of(p));
    }
}
