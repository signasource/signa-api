package com.signasource.signa_api.admin.service;

import com.signasource.signa_api.admin.dto.AdminPurchaseDetailResponse;
import com.signasource.signa_api.admin.dto.AdminPurchaseListItemResponse;
import com.signasource.signa_api.admin.dto.AdminRefundRequestResponse;
import com.signasource.signa_api.admin.dto.ConfirmPaymentRequest;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.entity.GemOrder;
import com.signasource.signa_api.gamification.entity.GemOrderStatus;
import com.signasource.signa_api.gamification.repository.GemOrderRepository;
import com.signasource.signa_api.learning.entity.CourseOrder;
import com.signasource.signa_api.learning.entity.CourseOrderStatus;
import com.signasource.signa_api.learning.repository.CourseOrderRepository;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.repository.RefundRequestRepository;
import com.signasource.signa_api.organizations.service.PurchasePaymentService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminPurchasesService {

    public static final String TYPE_SEATS = "SEATS";
    public static final String TYPE_GEMS = "GEMS";
    public static final String TYPE_COURSE = "COURSE";

    private final CoursePurchaseRepository seatsPurchaseRepository;
    private final GemOrderRepository gemOrderRepository;
    private final CourseOrderRepository courseOrderRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final AdminRefundRequestService refundRequestService;
    private final PurchasePaymentService paymentService;

    @Transactional(readOnly = true)
    public List<AdminPurchaseListItemResponse> listAll() {
        Set<String> pendingKeys =
                refundRequestRepository.findAllByOrderByCreatedAtDesc().stream()
                        .filter(r -> r.getStatus().name().equals("PENDING"))
                        .map(r -> r.getPurchaseType() + ":" + r.getPurchaseId())
                        .collect(Collectors.toSet());

        List<AdminPurchaseListItemResponse> result = new ArrayList<>();
        result.addAll(mapSeats(pendingKeys));
        result.addAll(mapGems(pendingKeys));
        result.addAll(mapCourseOrders(pendingKeys));

        result.sort(Comparator.comparing(AdminPurchaseListItemResponse::createdAt).reversed());
        return result;
    }

    @Transactional(readOnly = true)
    public AdminPurchaseDetailResponse detail(UUID id, String type) {
        return switch (type) {
            case TYPE_SEATS -> detailSeats(id);
            case TYPE_GEMS -> detailGems(id);
            case TYPE_COURSE -> detailCourseOrder(id);
            default -> throw new NotFoundException("Unknown purchase type: " + type);
        };
    }

    @Transactional
    public AdminPurchaseDetailResponse confirmPayment(
            UUID purchaseId, ConfirmPaymentRequest req, String adminName) {
        paymentService.confirmPaymentFull(purchaseId, req, adminName);
        return detailSeats(purchaseId);
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private List<AdminPurchaseListItemResponse> mapSeats(Set<String> pendingKeys) {
        return seatsPurchaseRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(p -> seatsToListItem(p, pendingKeys.contains(TYPE_SEATS + ":" + p.getId())))
                .toList();
    }

    private List<AdminPurchaseListItemResponse> mapGems(Set<String> pendingKeys) {
        return gemOrderRepository.findAll().stream()
                .sorted(Comparator.comparing(GemOrder::getCreatedAt).reversed())
                .map(o -> gemToListItem(o, pendingKeys.contains(TYPE_GEMS + ":" + o.getId())))
                .toList();
    }

    private List<AdminPurchaseListItemResponse> mapCourseOrders(Set<String> pendingKeys) {
        return courseOrderRepository.findAll().stream()
                .sorted(Comparator.comparing(CourseOrder::getCreatedAt).reversed())
                .map(
                        o ->
                                courseOrderToListItem(
                                        o, pendingKeys.contains(TYPE_COURSE + ":" + o.getId())))
                .toList();
    }

    private AdminPurchaseListItemResponse seatsToListItem(CoursePurchase p, boolean pendingRefund) {
        String mpOrderId = mpOrderIdFromRef(p.getPaymentReference());
        String manualRef = manualRefFromRef(p.getPaymentReference());
        String buyerSub =
                p.getCreatedBy() != null
                        ? p.getCreatedBy().getName()
                                + " "
                                + p.getCreatedBy().getLastName()
                                + " · admin"
                        : "admin";
        String buyerEmail =
                p.getCreatedBy() != null
                        ? p.getCreatedBy().getEmail()
                        : p.getOrganization().getContactEmail();
        String paymentMethodStr =
                p.getPaymentMethod() != null
                        ? p.getPaymentMethod().name()
                        : (mpOrderId != null ? "MERCADO_PAGO" : null);
        return new AdminPurchaseListItemResponse(
                p.getId(),
                TYPE_SEATS,
                p.getCourse().getName(),
                p.getSeats() + " cupos × " + p.getDurationDays() + " días",
                p.getOrganization().getName(),
                buyerSub,
                buyerEmail,
                p.getTotalAmount(),
                p.getCurrency(),
                p.getStatus().name(),
                p.getCreatedAt(),
                p.getPaidAt(),
                p.getRefundedAt(),
                mpOrderId,
                paymentMethodStr,
                manualRef,
                p.getConfirmedByName(),
                p.getConfirmedAt(),
                "purchase_" + hex8(p.getId()),
                pendingRefund);
    }

    private AdminPurchaseListItemResponse gemToListItem(GemOrder o, boolean pendingRefund) {
        String user = o.getUser().getName() + " " + o.getUser().getLastName();
        return new AdminPurchaseListItemResponse(
                o.getId(),
                TYPE_GEMS,
                gemPackName(o.getGemPack().getGems()),
                o.getGems() + " gemas",
                user,
                "@" + o.getUser().getUsername(),
                o.getUser().getEmail(),
                o.getAmount(),
                o.getCurrency(),
                gemStatusToUnified(o.getStatus()),
                o.getCreatedAt(),
                o.getPaidAt(),
                o.getRefundedAt(),
                o.getMercadoPagoOrderId(),
                "MERCADO_PAGO",
                null,
                null,
                null,
                "gems_" + hex8(o.getId()),
                pendingRefund);
    }

    private AdminPurchaseListItemResponse courseOrderToListItem(
            CourseOrder o, boolean pendingRefund) {
        String user = o.getUser().getName() + " " + o.getUser().getLastName();
        return new AdminPurchaseListItemResponse(
                o.getId(),
                TYPE_COURSE,
                o.getCourse().getName(),
                "Acceso permanente",
                user,
                "@" + o.getUser().getUsername(),
                o.getUser().getEmail(),
                o.getAmount(),
                o.getCurrency(),
                courseStatusToUnified(o.getStatus()),
                o.getCreatedAt(),
                o.getPaidAt(),
                o.getRefundedAt(),
                o.getMercadoPagoOrderId(),
                "MERCADO_PAGO",
                null,
                null,
                null,
                "course_" + hex8(o.getId()),
                pendingRefund);
    }

    private AdminPurchaseDetailResponse detailSeats(UUID id) {
        CoursePurchase p =
                seatsPurchaseRepository
                        .findForUpdateById(id)
                        .orElseThrow(() -> new NotFoundException("Purchase not found"));
        List<AdminRefundRequestResponse> requests =
                refundRequestService.listForPurchase(TYPE_SEATS, id);
        String mpOrderId = mpOrderIdFromRef(p.getPaymentReference());
        String manualRef = manualRefFromRef(p.getPaymentReference());
        String buyerSub =
                p.getCreatedBy() != null
                        ? p.getCreatedBy().getName()
                                + " "
                                + p.getCreatedBy().getLastName()
                                + " · admin"
                        : "admin";
        String buyerEmail =
                p.getCreatedBy() != null
                        ? p.getCreatedBy().getEmail()
                        : p.getOrganization().getContactEmail();
        String paymentMethodStr =
                p.getPaymentMethod() != null
                        ? p.getPaymentMethod().name()
                        : (mpOrderId != null ? "MERCADO_PAGO" : null);
        return new AdminPurchaseDetailResponse(
                p.getId(),
                TYPE_SEATS,
                p.getCourse().getName(),
                p.getSeats() + " cupos × " + p.getDurationDays() + " días",
                p.getOrganization().getName(),
                buyerSub,
                buyerEmail,
                p.getTotalAmount(),
                p.getCurrency(),
                p.getStatus().name(),
                p.getCreatedAt(),
                p.getPaidAt(),
                p.getRefundedAt(),
                mpOrderId,
                paymentMethodStr,
                manualRef,
                p.getConfirmedByName(),
                p.getConfirmedAt(),
                "purchase_" + hex8(p.getId()),
                p.getOrganization().getId(),
                p.getSeats(),
                p.getDurationDays(),
                p.getDiscountPercent(),
                null,
                requests);
    }

    private AdminPurchaseDetailResponse detailGems(UUID id) {
        GemOrder o =
                gemOrderRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException("Gem order not found"));
        List<AdminRefundRequestResponse> requests =
                refundRequestService.listForPurchase(TYPE_GEMS, id);
        String user = o.getUser().getName() + " " + o.getUser().getLastName();
        return new AdminPurchaseDetailResponse(
                o.getId(),
                TYPE_GEMS,
                gemPackName(o.getGemPack().getGems()),
                o.getGems() + " gemas",
                user,
                "@" + o.getUser().getUsername(),
                o.getUser().getEmail(),
                o.getAmount(),
                o.getCurrency(),
                gemStatusToUnified(o.getStatus()),
                o.getCreatedAt(),
                o.getPaidAt(),
                o.getRefundedAt(),
                o.getMercadoPagoOrderId(),
                "MERCADO_PAGO",
                null,
                null,
                null,
                "gems_" + hex8(o.getId()),
                null,
                null,
                null,
                null,
                o.getGems(),
                requests);
    }

    private AdminPurchaseDetailResponse detailCourseOrder(UUID id) {
        CourseOrder o =
                courseOrderRepository
                        .findById(id)
                        .orElseThrow(() -> new NotFoundException("Course order not found"));
        List<AdminRefundRequestResponse> requests =
                refundRequestService.listForPurchase(TYPE_COURSE, id);
        String user = o.getUser().getName() + " " + o.getUser().getLastName();
        return new AdminPurchaseDetailResponse(
                o.getId(),
                TYPE_COURSE,
                o.getCourse().getName(),
                "Acceso permanente",
                user,
                "@" + o.getUser().getUsername(),
                o.getUser().getEmail(),
                o.getAmount(),
                o.getCurrency(),
                courseStatusToUnified(o.getStatus()),
                o.getCreatedAt(),
                o.getPaidAt(),
                o.getRefundedAt(),
                o.getMercadoPagoOrderId(),
                "MERCADO_PAGO",
                null,
                null,
                null,
                "course_" + hex8(o.getId()),
                null,
                null,
                null,
                null,
                null,
                requests);
    }

    private static String gemPackName(int gems) {
        if (gems <= 350) return "Puñado de gemas";
        if (gems <= 1500) return "Bolsa de gemas";
        return "Cofre de gemas";
    }

    private static String gemStatusToUnified(GemOrderStatus s) {
        return switch (s) {
            case PENDING -> "PENDING_PAYMENT";
            case PAID -> "PAID";
            case REFUNDED -> "REFUNDED";
        };
    }

    private static String courseStatusToUnified(CourseOrderStatus s) {
        return switch (s) {
            case PENDING -> "PENDING_PAYMENT";
            case PAID -> "PAID";
            case REFUNDED -> "REFUNDED";
        };
    }

    private static String mpOrderIdFromRef(String ref) {
        if (ref != null && ref.startsWith(PurchasePaymentService.MERCADO_PAGO_REFERENCE_PREFIX)) {
            return ref.substring(PurchasePaymentService.MERCADO_PAGO_REFERENCE_PREFIX.length());
        }
        return null;
    }

    private static String manualRefFromRef(String ref) {
        if (ref == null || ref.startsWith(PurchasePaymentService.MERCADO_PAGO_REFERENCE_PREFIX)) {
            return null;
        }
        return ref;
    }

    private static String hex8(UUID id) {
        return id.toString().replace("-", "").substring(0, 8);
    }
}
