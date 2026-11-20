package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.admin.service.AdminRefundService;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.entity.GemOrder;
import com.signasource.signa_api.gamification.entity.GemOrderStatus;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.GemOrderRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.entity.CourseOrder;
import com.signasource.signa_api.learning.entity.CourseOrderStatus;
import com.signasource.signa_api.learning.repository.CourseOrderRepository;
import com.signasource.signa_api.learning.repository.UserLessonProgressRepository;
import com.signasource.signa_api.organizations.dto.CreateRefundRequestRequest;
import com.signasource.signa_api.organizations.dto.RefundEligibilityResponse;
import com.signasource.signa_api.organizations.dto.UserRefundRequestResponse;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.entity.RefundRequest;
import com.signasource.signa_api.organizations.entity.RefundRequestStatus;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.repository.RefundRequestRepository;
import com.signasource.signa_api.users.entity.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class UserRefundRequestService {

    public static final String TYPE_GEMS = "GEMS";
    public static final String TYPE_COURSE = "COURSE";
    public static final String TYPE_SEATS = "SEATS";

    private final RefundRequestRepository refundRequestRepository;
    private final GemOrderRepository gemOrderRepository;
    private final CourseOrderRepository courseOrderRepository;
    private final CoursePurchaseRepository coursePurchaseRepository;
    private final UserStatsRepository userStatsRepository;
    private final UserLessonProgressRepository lessonProgressRepository;
    private final AdminRefundService adminRefundService;
    private final TransactionTemplate transactionTemplate;

    // ── Eligibility checks ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RefundEligibilityResponse checkEligibilityGems(UUID orderId, User user) {
        GemOrder order =
                gemOrderRepository
                        .findByIdAndUserId(orderId, user.getId())
                        .orElseThrow(() -> new NotFoundException("Gem order not found"));
        return gemsEligibility(order, user);
    }

    @Transactional(readOnly = true)
    public RefundEligibilityResponse checkEligibilityCourse(UUID orderId, User user) {
        CourseOrder order =
                courseOrderRepository
                        .findByIdAndUserId(orderId, user.getId())
                        .orElseThrow(() -> new NotFoundException("Course order not found"));
        return courseEligibility(order, user);
    }

    // ── Create requests ───────────────────────────────────────────────────────

    /**
     * Saves the request and immediately processes the Mercado Pago refund, the same way an admin
     * resolving the request would. The MP call runs outside any database transaction; if it fails
     * the request stays PENDING so an admin can retry manually.
     */
    public UserRefundRequestResponse createForGemOrder(
            UUID orderId, User user, CreateRefundRequestRequest req) {
        record Saved(UUID requestId, String productId, BigDecimal amount, String currency) {}
        Saved saved =
                inTransaction(
                        () -> {
                            GemOrder order =
                                    gemOrderRepository
                                            .findByIdAndUserId(orderId, user.getId())
                                            .orElseThrow(
                                                    () ->
                                                            new NotFoundException(
                                                                    "Gem order not found"));
                            RefundEligibilityResponse elig = gemsEligibility(order, user);
                            if (!elig.eligible()) throw new InvalidInputException(elig.reason());
                            checkNoPendingRequest(TYPE_GEMS, orderId);
                            RefundRequest r = buildRequest(TYPE_GEMS, orderId, user, req, "app");
                            refundRequestRepository.save(r);
                            return new Saved(
                                    r.getId(),
                                    order.getGemPack().getProductId(),
                                    order.getAmount(),
                                    order.getCurrency());
                        });
        adminRefundService.refund(TYPE_GEMS, orderId, false);
        RefundRequest resolved =
                inTransaction(
                        () -> {
                            RefundRequest r =
                                    refundRequestRepository
                                            .findById(saved.requestId())
                                            .orElseThrow();
                            r.setStatus(RefundRequestStatus.RESOLVED);
                            r.setResolvedAt(Instant.now());
                            return refundRequestRepository.save(r);
                        });
        return UserRefundRequestResponse.from(
                resolved, saved.productId(), saved.amount(), saved.currency());
    }

    /**
     * Saves the request and immediately processes the Mercado Pago refund, the same way an admin
     * resolving the request would. The MP call runs outside any database transaction; if it fails
     * the request stays PENDING so an admin can retry manually.
     */
    public UserRefundRequestResponse createForCourseOrder(
            UUID orderId, User user, CreateRefundRequestRequest req) {
        record Saved(UUID requestId, String productName, BigDecimal amount, String currency) {}
        Saved saved =
                inTransaction(
                        () -> {
                            CourseOrder order =
                                    courseOrderRepository
                                            .findByIdAndUserId(orderId, user.getId())
                                            .orElseThrow(
                                                    () ->
                                                            new NotFoundException(
                                                                    "Course order not found"));
                            RefundEligibilityResponse elig = courseEligibility(order, user);
                            if (!elig.eligible()) throw new InvalidInputException(elig.reason());
                            checkNoPendingRequest(TYPE_COURSE, orderId);
                            RefundRequest r = buildRequest(TYPE_COURSE, orderId, user, req, "app");
                            refundRequestRepository.save(r);
                            return new Saved(
                                    r.getId(),
                                    order.getCourse().getName(),
                                    order.getAmount(),
                                    order.getCurrency());
                        });
        adminRefundService.refund(TYPE_COURSE, orderId, false);
        RefundRequest resolved =
                inTransaction(
                        () -> {
                            RefundRequest r =
                                    refundRequestRepository
                                            .findById(saved.requestId())
                                            .orElseThrow();
                            r.setStatus(RefundRequestStatus.RESOLVED);
                            r.setResolvedAt(Instant.now());
                            return refundRequestRepository.save(r);
                        });
        return UserRefundRequestResponse.from(
                resolved, saved.productName(), saved.amount(), saved.currency());
    }

    @Transactional
    public UserRefundRequestResponse createForSeats(
            UUID orgId, UUID purchaseId, User user, CreateRefundRequestRequest req) {
        CoursePurchase purchase =
                coursePurchaseRepository
                        .findById(purchaseId)
                        .filter(p -> p.getOrganization().getId().equals(orgId))
                        .orElseThrow(() -> new NotFoundException("Purchase not found"));
        RefundEligibilityResponse elig = seatsEligibility(purchase);
        if (!elig.eligible()) throw new InvalidInputException(elig.reason());
        checkNoPendingRequest(TYPE_SEATS, purchaseId);

        RefundRequest r = buildRequest(TYPE_SEATS, purchaseId, user, req, "web");
        refundRequestRepository.save(r);
        String productName =
                purchase.getCourse().getName() + " · " + purchase.getSeats() + " cupos";
        return UserRefundRequestResponse.from(
                r, productName, purchase.getTotalAmount(), purchase.getCurrency());
    }

    // ── List user's own requests ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<UserRefundRequestResponse> listForUser(User user) {
        return refundRequestRepository
                .findByRequestedByIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(r -> UserRefundRequestResponse.from(r, null, null, null))
                .toList();
    }

    // ── Status map for history endpoints ─────────────────────────────────────

    /**
     * Returns a map from purchaseId → refund request status name for the given user and purchase
     * type. PENDING takes precedence over RESOLVED when multiple requests exist for the same
     * purchase.
     */
    @Transactional(readOnly = true)
    public Map<UUID, String> getRefundStatusMap(UUID userId, String purchaseType) {
        List<RefundRequest> requests =
                refundRequestRepository.findByRequestedByIdAndPurchaseType(userId, purchaseType);
        Map<UUID, String> map = new HashMap<>();
        for (RefundRequest r : requests) {
            map.merge(
                    r.getPurchaseId(),
                    r.getStatus().name(),
                    (existing, incoming) -> "PENDING".equals(existing) ? existing : incoming);
        }
        return map;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private RefundEligibilityResponse gemsEligibility(GemOrder order, User user) {
        if (order.getStatus() != GemOrderStatus.PAID) {
            return RefundEligibilityResponse.no("La compra no está pagada");
        }
        if (order.getRefundedAt() != null) {
            return RefundEligibilityResponse.no("Esta compra ya fue reembolsada");
        }
        UserStats stats = userStatsRepository.findByUserId(user.getId()).orElse(null);
        int balance = stats != null ? stats.getGems() : 0;
        if (balance < order.getGems()) {
            return RefundEligibilityResponse.no("Ya usaste parte de las gemas de esta compra");
        }
        return RefundEligibilityResponse.ok();
    }

    private RefundEligibilityResponse courseEligibility(CourseOrder order, User user) {
        if (order.getStatus() != CourseOrderStatus.PAID) {
            return RefundEligibilityResponse.no("La compra no está pagada");
        }
        if (order.getRefundedAt() != null) {
            return RefundEligibilityResponse.no("Esta compra ya fue reembolsada");
        }
        boolean hasProgress =
                lessonProgressRepository.existsCompletedLessonForCourse(
                        user.getId(), order.getCourse().getId());
        if (hasProgress) {
            return RefundEligibilityResponse.no("Ya completaste la primera lección del curso");
        }
        return RefundEligibilityResponse.ok();
    }

    private RefundEligibilityResponse seatsEligibility(CoursePurchase purchase) {
        if (purchase.getStatus() != PurchaseStatus.PAID) {
            return RefundEligibilityResponse.no("La compra no está pagada");
        }
        if (purchase.getSeatList().stream().anyMatch(PurchaseSeat::hasAccepted)) {
            return RefundEligibilityResponse.no(
                    "No se puede reembolsar: al menos un invitado ya aceptó su invitación");
        }
        return RefundEligibilityResponse.ok();
    }

    private void checkNoPendingRequest(String type, UUID purchaseId) {
        boolean exists =
                refundRequestRepository.findByPurchaseTypeAndPurchaseId(type, purchaseId).stream()
                        .anyMatch(r -> r.getStatus() == RefundRequestStatus.PENDING);
        if (exists)
            throw new InvalidInputException(
                    "Ya existe un pedido de reembolso pendiente para esta compra");
    }

    private RefundRequest buildRequest(
            String type,
            UUID purchaseId,
            User user,
            CreateRefundRequestRequest req,
            String channel) {
        return RefundRequest.builder()
                .purchaseType(type)
                .purchaseId(purchaseId)
                .requestedBy(user)
                .reason(req.reason())
                .channel(channel)
                .message(
                        req.message() == null || req.message().isBlank()
                                ? null
                                : req.message().strip())
                .status(RefundRequestStatus.PENDING)
                .build();
    }

    /** Runs {@code work} in a transaction; the callbacks here never return null. */
    private <T> T inTransaction(Supplier<T> work) {
        T result = transactionTemplate.execute(status -> work.get());
        if (result == null) {
            throw new IllegalStateException("Transaction callback returned null");
        }
        return result;
    }
}
