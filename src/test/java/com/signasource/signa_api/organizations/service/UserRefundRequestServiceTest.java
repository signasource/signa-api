package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.entity.GemOrder;
import com.signasource.signa_api.gamification.entity.GemOrderStatus;
import com.signasource.signa_api.gamification.entity.GemPack;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.GemOrderRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseOrder;
import com.signasource.signa_api.learning.entity.CourseOrderStatus;
import com.signasource.signa_api.learning.repository.CourseOrderRepository;
import com.signasource.signa_api.learning.repository.UserLessonProgressRepository;
import com.signasource.signa_api.organizations.dto.CreateRefundRequestRequest;
import com.signasource.signa_api.organizations.dto.RefundEligibilityResponse;
import com.signasource.signa_api.organizations.dto.UserRefundRequestResponse;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.entity.RefundRequest;
import com.signasource.signa_api.organizations.entity.RefundRequestReason;
import com.signasource.signa_api.organizations.entity.RefundRequestStatus;
import com.signasource.signa_api.organizations.entity.SeatStatus;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.repository.RefundRequestRepository;
import com.signasource.signa_api.users.entity.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserRefundRequestServiceTest {

    @Mock private RefundRequestRepository refundRequestRepository;
    @Mock private GemOrderRepository gemOrderRepository;
    @Mock private CourseOrderRepository courseOrderRepository;
    @Mock private CoursePurchaseRepository coursePurchaseRepository;
    @Mock private UserStatsRepository userStatsRepository;
    @Mock private UserLessonProgressRepository lessonProgressRepository;

    @InjectMocks private UserRefundRequestService service;

    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder().id(userId).build();
    }

    // ── checkEligibilityGems ──────────────────────────────────────────────────

    @Test
    void checkEligibilityGems_throwsWhenOrderNotFound() {
        UUID orderId = UUID.randomUUID();
        when(gemOrderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.checkEligibilityGems(orderId, user));
    }

    @Test
    void checkEligibilityGems_notEligibleWhenNotPaid() {
        UUID orderId = UUID.randomUUID();
        GemOrder order = paidGemOrder(orderId, 100);
        order.setStatus(GemOrderStatus.PENDING);
        when(gemOrderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));

        RefundEligibilityResponse result = service.checkEligibilityGems(orderId, user);

        assertFalse(result.eligible());
    }

    @Test
    void checkEligibilityGems_notEligibleWhenAlreadyRefunded() {
        UUID orderId = UUID.randomUUID();
        GemOrder order = paidGemOrder(orderId, 100);
        order.setRefundedAt(Instant.now());
        when(gemOrderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));

        RefundEligibilityResponse result = service.checkEligibilityGems(orderId, user);

        assertFalse(result.eligible());
    }

    @Test
    void checkEligibilityGems_notEligibleWhenGemsUsed() {
        UUID orderId = UUID.randomUUID();
        GemOrder order = paidGemOrder(orderId, 100);
        when(gemOrderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));
        UserStats stats = new UserStats();
        stats.setGems(50);
        when(userStatsRepository.findByUserId(userId)).thenReturn(Optional.of(stats));

        RefundEligibilityResponse result = service.checkEligibilityGems(orderId, user);

        assertFalse(result.eligible());
    }

    @Test
    void checkEligibilityGems_eligibleWhenFullBalanceAvailable() {
        UUID orderId = UUID.randomUUID();
        GemOrder order = paidGemOrder(orderId, 100);
        when(gemOrderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));
        UserStats stats = new UserStats();
        stats.setGems(100);
        when(userStatsRepository.findByUserId(userId)).thenReturn(Optional.of(stats));

        RefundEligibilityResponse result = service.checkEligibilityGems(orderId, user);

        assertTrue(result.eligible());
    }

    // ── checkEligibilityCourse ────────────────────────────────────────────────

    @Test
    void checkEligibilityCourse_throwsWhenOrderNotFound() {
        UUID orderId = UUID.randomUUID();
        when(courseOrderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.checkEligibilityCourse(orderId, user));
    }

    @Test
    void checkEligibilityCourse_notEligibleWhenNotPaid() {
        UUID orderId = UUID.randomUUID();
        CourseOrder order = paidCourseOrder(orderId);
        when(order.getStatus()).thenReturn(CourseOrderStatus.PENDING);
        when(courseOrderRepository.findByIdAndUserId(orderId, userId))
                .thenReturn(Optional.of(order));

        RefundEligibilityResponse result = service.checkEligibilityCourse(orderId, user);

        assertFalse(result.eligible());
    }

    @Test
    void checkEligibilityCourse_notEligibleWhenHasProgress() {
        UUID orderId = UUID.randomUUID();
        CourseOrder order = paidCourseOrder(orderId);
        when(courseOrderRepository.findByIdAndUserId(orderId, userId))
                .thenReturn(Optional.of(order));
        when(lessonProgressRepository.existsCompletedLessonForCourse(
                        userId, order.getCourse().getId()))
                .thenReturn(true);

        RefundEligibilityResponse result = service.checkEligibilityCourse(orderId, user);

        assertFalse(result.eligible());
    }

    @Test
    void checkEligibilityCourse_eligibleWhenNoProgress() {
        UUID orderId = UUID.randomUUID();
        CourseOrder order = paidCourseOrder(orderId);
        when(courseOrderRepository.findByIdAndUserId(orderId, userId))
                .thenReturn(Optional.of(order));
        when(lessonProgressRepository.existsCompletedLessonForCourse(
                        userId, order.getCourse().getId()))
                .thenReturn(false);

        RefundEligibilityResponse result = service.checkEligibilityCourse(orderId, user);

        assertTrue(result.eligible());
    }

    // ── createForGemOrder ─────────────────────────────────────────────────────

    @Test
    void createForGemOrder_throwsWhenAlreadyPending() {
        UUID orderId = UUID.randomUUID();
        GemOrder order = paidGemOrder(orderId, 100);
        when(gemOrderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));
        UserStats stats = new UserStats();
        stats.setGems(100);
        when(userStatsRepository.findByUserId(userId)).thenReturn(Optional.of(stats));
        RefundRequest pending = mock(RefundRequest.class);
        when(pending.getStatus()).thenReturn(RefundRequestStatus.PENDING);
        when(refundRequestRepository.findByPurchaseTypeAndPurchaseId(
                        UserRefundRequestService.TYPE_GEMS, orderId))
                .thenReturn(List.of(pending));

        assertThrows(
                InvalidInputException.class,
                () ->
                        service.createForGemOrder(
                                orderId,
                                user,
                                new CreateRefundRequestRequest(
                                        RefundRequestReason.MISTAKE, "motivo")));
        verify(refundRequestRepository, never()).save(any());
    }

    @Test
    void createForGemOrder_savesAndReturnsResponseWhenEligible() {
        UUID orderId = UUID.randomUUID();
        GemOrder order = paidGemOrder(orderId, 100);
        when(gemOrderRepository.findByIdAndUserId(orderId, userId)).thenReturn(Optional.of(order));
        UserStats stats = new UserStats();
        stats.setGems(100);
        when(userStatsRepository.findByUserId(userId)).thenReturn(Optional.of(stats));
        when(refundRequestRepository.findByPurchaseTypeAndPurchaseId(
                        UserRefundRequestService.TYPE_GEMS, orderId))
                .thenReturn(List.of());
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserRefundRequestResponse result =
                service.createForGemOrder(
                        orderId,
                        user,
                        new CreateRefundRequestRequest(RefundRequestReason.MISTAKE, "motivo"));

        verify(refundRequestRepository).save(any(RefundRequest.class));
        assertEquals(UserRefundRequestService.TYPE_GEMS, result.purchaseType());
    }

    // ── createForCourseOrder ──────────────────────────────────────────────────

    @Test
    void createForCourseOrder_savesAndReturnsResponseWhenEligible() {
        UUID orderId = UUID.randomUUID();
        CourseOrder order = paidCourseOrder(orderId);
        when(courseOrderRepository.findByIdAndUserId(orderId, userId))
                .thenReturn(Optional.of(order));
        when(lessonProgressRepository.existsCompletedLessonForCourse(
                        userId, order.getCourse().getId()))
                .thenReturn(false);
        when(refundRequestRepository.findByPurchaseTypeAndPurchaseId(
                        UserRefundRequestService.TYPE_COURSE, orderId))
                .thenReturn(List.of());
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserRefundRequestResponse result =
                service.createForCourseOrder(
                        orderId,
                        user,
                        new CreateRefundRequestRequest(RefundRequestReason.MISTAKE, "motivo"));

        verify(refundRequestRepository).save(any(RefundRequest.class));
        assertEquals(UserRefundRequestService.TYPE_COURSE, result.purchaseType());
    }

    // ── createForSeats ────────────────────────────────────────────────────────

    @Test
    void createForSeats_throwsWhenPurchaseNotFound() {
        UUID orgId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        when(coursePurchaseRepository.findById(purchaseId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () ->
                        service.createForSeats(
                                orgId,
                                purchaseId,
                                user,
                                new CreateRefundRequestRequest(
                                        RefundRequestReason.MISTAKE, "motivo")));
    }

    @Test
    void createForSeats_notEligibleWhenActiveSeatsExist() {
        UUID orgId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        CoursePurchase purchase = paidPurchase(purchaseId, orgId);
        PurchaseSeat activeSeat = mock(PurchaseSeat.class);
        when(activeSeat.getStatus()).thenReturn(SeatStatus.ACTIVE);
        when(purchase.getSeatList()).thenReturn(List.of(activeSeat));
        when(coursePurchaseRepository.findById(purchaseId)).thenReturn(Optional.of(purchase));

        assertThrows(
                InvalidInputException.class,
                () ->
                        service.createForSeats(
                                orgId,
                                purchaseId,
                                user,
                                new CreateRefundRequestRequest(
                                        RefundRequestReason.MISTAKE, "motivo")));
    }

    @Test
    void createForSeats_savesWhenEligible() {
        UUID orgId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        CoursePurchase purchase = paidPurchase(purchaseId, orgId);
        when(purchase.getSeatList()).thenReturn(List.of());
        when(coursePurchaseRepository.findById(purchaseId)).thenReturn(Optional.of(purchase));
        when(refundRequestRepository.findByPurchaseTypeAndPurchaseId(
                        UserRefundRequestService.TYPE_SEATS, purchaseId))
                .thenReturn(List.of());
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UserRefundRequestResponse result =
                service.createForSeats(
                        orgId,
                        purchaseId,
                        user,
                        new CreateRefundRequestRequest(RefundRequestReason.MISTAKE, "motivo"));

        verify(refundRequestRepository).save(any(RefundRequest.class));
        assertEquals(UserRefundRequestService.TYPE_SEATS, result.purchaseType());
    }

    // ── listForUser ───────────────────────────────────────────────────────────

    @Test
    void listForUser_returnsEmptyList() {
        when(refundRequestRepository.findByRequestedByIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of());

        List<UserRefundRequestResponse> result = service.listForUser(user);

        assertTrue(result.isEmpty());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private GemOrder paidGemOrder(UUID orderId, int gems) {
        GemPack pack = mock(GemPack.class);
        when(pack.getProductId()).thenReturn("pack-100");
        GemOrder order = new GemOrder();
        order.setId(orderId);
        order.setGems(gems);
        order.setAmount(BigDecimal.valueOf(500));
        order.setCurrency("ARS");
        order.setStatus(GemOrderStatus.PAID);
        order.setGemPack(pack);
        return order;
    }

    private CourseOrder paidCourseOrder(UUID orderId) {
        Course course = mock(Course.class);
        UUID courseId = UUID.randomUUID();
        when(course.getId()).thenReturn(courseId);
        when(course.getName()).thenReturn("Curso de prueba");
        CourseOrder order = mock(CourseOrder.class);
        when(order.getId()).thenReturn(orderId);
        when(order.getStatus()).thenReturn(CourseOrderStatus.PAID);
        when(order.getRefundedAt()).thenReturn(null);
        when(order.getCourse()).thenReturn(course);
        when(order.getAmount()).thenReturn(BigDecimal.valueOf(2000));
        when(order.getCurrency()).thenReturn("ARS");
        return order;
    }

    private CoursePurchase paidPurchase(UUID purchaseId, UUID orgId) {
        Organization org = mock(Organization.class);
        when(org.getId()).thenReturn(orgId);
        Course course = mock(Course.class);
        when(course.getName()).thenReturn("Curso org");
        CoursePurchase purchase = mock(CoursePurchase.class);
        when(purchase.getId()).thenReturn(purchaseId);
        when(purchase.getOrganization()).thenReturn(org);
        when(purchase.getStatus()).thenReturn(PurchaseStatus.PAID);
        when(purchase.getCourse()).thenReturn(course);
        when(purchase.getSeats()).thenReturn(5);
        when(purchase.getTotalAmount()).thenReturn(BigDecimal.valueOf(10000));
        when(purchase.getCurrency()).thenReturn("ARS");
        return purchase;
    }
}
