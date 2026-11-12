package com.signasource.signa_api.organizations.service;

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
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.entity.RefundRequest;
import com.signasource.signa_api.organizations.entity.RefundRequestStatus;
import com.signasource.signa_api.organizations.entity.SeatStatus;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.repository.RefundRequestRepository;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserRefundRequestService {

    static final String TYPE_GEMS = "GEMS";
    static final String TYPE_COURSE = "COURSE";
    static final String TYPE_SEATS = "SEATS";

    private final RefundRequestRepository refundRequestRepository;
    private final GemOrderRepository gemOrderRepository;
    private final CourseOrderRepository courseOrderRepository;
    private final CoursePurchaseRepository coursePurchaseRepository;
    private final UserStatsRepository userStatsRepository;
    private final UserLessonProgressRepository lessonProgressRepository;

    // ── Eligibility checks ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public RefundEligibilityResponse checkEligibilityGems(UUID orderId, User user) {
        GemOrder order = gemOrderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new NotFoundException("Gem order not found"));
        return gemsEligibility(order, user);
    }

    @Transactional(readOnly = true)
    public RefundEligibilityResponse checkEligibilityCourse(UUID orderId, User user) {
        CourseOrder order = courseOrderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new NotFoundException("Course order not found"));
        return courseEligibility(order, user);
    }

    // ── Create requests ───────────────────────────────────────────────────────

    @Transactional
    public UserRefundRequestResponse createForGemOrder(
            UUID orderId, User user, CreateRefundRequestRequest req) {
        GemOrder order = gemOrderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new NotFoundException("Gem order not found"));
        RefundEligibilityResponse elig = gemsEligibility(order, user);
        if (!elig.eligible()) throw new InvalidInputException(elig.reason());
        checkNoPendingRequest(TYPE_GEMS, orderId);

        RefundRequest r = buildRequest(TYPE_GEMS, orderId, user, req, "app");
        refundRequestRepository.save(r);
        return UserRefundRequestResponse.from(
                r, order.getGemPack().getProductId(), order.getAmount(), order.getCurrency());
    }

    @Transactional
    public UserRefundRequestResponse createForCourseOrder(
            UUID orderId, User user, CreateRefundRequestRequest req) {
        CourseOrder order = courseOrderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new NotFoundException("Course order not found"));
        RefundEligibilityResponse elig = courseEligibility(order, user);
        if (!elig.eligible()) throw new InvalidInputException(elig.reason());
        checkNoPendingRequest(TYPE_COURSE, orderId);

        RefundRequest r = buildRequest(TYPE_COURSE, orderId, user, req, "app");
        refundRequestRepository.save(r);
        return UserRefundRequestResponse.from(
                r, order.getCourse().getName(), order.getAmount(), order.getCurrency());
    }

    @Transactional
    public UserRefundRequestResponse createForSeats(
            UUID orgId, UUID purchaseId, User user, CreateRefundRequestRequest req) {
        CoursePurchase purchase = coursePurchaseRepository.findById(purchaseId)
                .filter(p -> p.getOrganization().getId().equals(orgId))
                .orElseThrow(() -> new NotFoundException("Purchase not found"));
        RefundEligibilityResponse elig = seatsEligibility(purchase);
        if (!elig.eligible()) throw new InvalidInputException(elig.reason());
        checkNoPendingRequest(TYPE_SEATS, purchaseId);

        RefundRequest r = buildRequest(TYPE_SEATS, purchaseId, user, req, "web");
        refundRequestRepository.save(r);
        String productName = purchase.getCourse().getName()
                + " · " + purchase.getSeats() + " cupos";
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
        boolean hasProgress = lessonProgressRepository.existsCompletedLessonForCourse(
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
        boolean anyActive = purchase.getSeatList().stream()
                .anyMatch(s -> s.getStatus() == SeatStatus.ACTIVE);
        if (anyActive) {
            return RefundEligibilityResponse.no("Hay participantes que ya aceptaron los cupos");
        }
        return RefundEligibilityResponse.ok();
    }

    private void checkNoPendingRequest(String type, UUID purchaseId) {
        boolean exists = refundRequestRepository.findByPurchaseTypeAndPurchaseId(type, purchaseId)
                .stream().anyMatch(r -> r.getStatus() == RefundRequestStatus.PENDING);
        if (exists) throw new InvalidInputException("Ya existe un pedido de reembolso pendiente para esta compra");
    }

    private RefundRequest buildRequest(
            String type, UUID purchaseId, User user,
            CreateRefundRequestRequest req, String channel) {
        return RefundRequest.builder()
                .purchaseType(type)
                .purchaseId(purchaseId)
                .requestedBy(user)
                .reason(req.reason())
                .channel(channel)
                .message(req.message())
                .status(RefundRequestStatus.PENDING)
                .build();
    }
}
