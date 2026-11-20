package com.signasource.signa_api.admin.service;

import com.signasource.signa_api.admin.dto.AdminRefundRequestResponse;
import com.signasource.signa_api.admin.dto.UpdateRefundRequestRequest;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.entity.GemOrder;
import com.signasource.signa_api.gamification.entity.GemOrderStatus;
import com.signasource.signa_api.gamification.repository.GemOrderRepository;
import com.signasource.signa_api.learning.entity.CourseOrder;
import com.signasource.signa_api.learning.entity.CourseOrderStatus;
import com.signasource.signa_api.learning.repository.CourseOrderRepository;
import com.signasource.signa_api.organizations.entity.PurchaseRefund;
import com.signasource.signa_api.organizations.entity.RefundReason;
import com.signasource.signa_api.organizations.entity.RefundRequest;
import com.signasource.signa_api.organizations.entity.RefundRequestStatus;
import com.signasource.signa_api.organizations.entity.RefundStatus;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.repository.PurchaseRefundRepository;
import com.signasource.signa_api.organizations.repository.RefundRequestRepository;
import com.signasource.signa_api.users.entity.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class AdminRefundRequestService {

    private final RefundRequestRepository repository;
    private final PurchaseRefundRepository purchaseRefundRepository;
    private final CoursePurchaseRepository seatsPurchaseRepository;
    private final GemOrderRepository gemOrderRepository;
    private final CourseOrderRepository courseOrderRepository;
    private final AdminRefundService adminRefundService;
    private final TransactionTemplate transactionTemplate;

    /**
     * Every refund request plus every refunded purchase nobody asked for (seats, gems and courses).
     * A purchase that has a request is shown through it, so a resolved request and the refund it
     * caused never appear twice.
     */
    @Transactional(readOnly = true)
    public List<AdminRefundRequestResponse> listAll() {
        List<RefundRequest> requests = repository.findAllByOrderByCreatedAtDesc();
        Set<String> requested =
                requests.stream()
                        .map(r -> key(r.getPurchaseType(), r.getPurchaseId()))
                        .collect(Collectors.toSet());
        List<AdminRefundRequestResponse> result =
                new ArrayList<>(requests.stream().map(this::toResponse).toList());
        purchaseRefundRepository.findByStatusOrderByRefundedAtDesc(RefundStatus.REFUNDED).stream()
                .filter(
                        refund ->
                                !refund.getReason().revokesPurchase()
                                        || !requested.contains(
                                                key(
                                                        AdminPurchasesService.TYPE_SEATS,
                                                        refund.getPurchase().getId())))
                .map(this::toResponse)
                .forEach(result::add);
        gemOrderRepository.findByStatus(GemOrderStatus.REFUNDED).stream()
                .filter(o -> !requested.contains(key(AdminPurchasesService.TYPE_GEMS, o.getId())))
                .map(this::toResponse)
                .forEach(result::add);
        courseOrderRepository.findByStatus(CourseOrderStatus.REFUNDED).stream()
                .filter(o -> !requested.contains(key(AdminPurchasesService.TYPE_COURSE, o.getId())))
                .map(this::toResponse)
                .forEach(result::add);
        result.sort(Comparator.comparing(AdminRefundRequestResponse::createdAt).reversed());
        return result;
    }

    private static String key(String type, UUID purchaseId) {
        return type + ":" + purchaseId;
    }

    /** A gem order refunded without a request (admin, Mercado Pago, chargeback), read-only. */
    private AdminRefundRequestResponse toResponse(GemOrder o) {
        return informational(
                o.getId(),
                AdminPurchasesService.TYPE_GEMS,
                o.getUser(),
                o.getRefundedAt() != null ? o.getRefundedAt() : o.getCreatedAt(),
                new PurchaseSummary(
                        o.getGemPack().getProductId(),
                        o.getGems() + " gemas",
                        o.getAmount(),
                        o.getCurrency(),
                        gemStatus(o.getStatus().name()),
                        o.getPaidAt()));
    }

    /** A course order refunded without a request (admin, Mercado Pago, chargeback), read-only. */
    private AdminRefundRequestResponse toResponse(CourseOrder o) {
        return informational(
                o.getId(),
                AdminPurchasesService.TYPE_COURSE,
                o.getUser(),
                o.getRefundedAt() != null ? o.getRefundedAt() : o.getCreatedAt(),
                new PurchaseSummary(
                        o.getCourse().getName(),
                        "Acceso permanente",
                        o.getAmount(),
                        o.getCurrency(),
                        courseStatus(o.getStatus().name()),
                        o.getPaidAt()));
    }

    /**
     * Orders keep no refund row, so the id is the order's own and the reason is the generic Mercado
     * Pago one: every way an order gets refunded goes through Mercado Pago.
     */
    private static AdminRefundRequestResponse informational(
            UUID orderId, String type, User buyer, Instant at, PurchaseSummary summary) {
        return new AdminRefundRequestResponse(
                orderId,
                type,
                orderId,
                RefundReason.EXTERNAL_REFUND.name(),
                "system",
                "",
                null,
                RefundRequestStatus.RESOLVED.name(),
                null,
                at,
                at,
                buyer.getName() + " " + buyer.getLastName(),
                "@" + buyer.getUsername(),
                buyer.getEmail(),
                summary.productName(),
                summary.productDetail(),
                summary.amount(),
                summary.currency(),
                summary.status(),
                summary.paidAt(),
                true);
    }

    /** Seat refunds nobody requested (withdrawal, Mercado Pago, chargeback), read-only. */
    private AdminRefundRequestResponse toResponse(PurchaseRefund refund) {
        var purchase = refund.getPurchase();
        var by = refund.getRequestedBy();
        Instant at =
                refund.getRefundedAt() != null ? refund.getRefundedAt() : refund.getCreatedAt();
        return new AdminRefundRequestResponse(
                refund.getId(),
                AdminPurchasesService.TYPE_SEATS,
                purchase.getId(),
                refund.getReason().name(),
                "system",
                refund.getNote() != null ? refund.getNote() : "",
                null,
                RefundRequestStatus.RESOLVED.name(),
                null,
                at,
                at,
                by != null
                        ? by.getName() + " " + by.getLastName()
                        : purchase.getOrganization().getName(),
                by != null ? "@" + by.getUsername() : "",
                by != null ? by.getEmail() : purchase.getOrganization().getContactEmail(),
                purchase.getCourse().getName(),
                purchase.getSeats() + " cupos × " + purchase.getDurationDays() + " días",
                refund.getAmount(),
                refund.getCurrency(),
                purchase.getStatus().name(),
                purchase.getPaidAt(),
                true);
    }

    @Transactional(readOnly = true)
    public List<AdminRefundRequestResponse> listForPurchase(String type, UUID purchaseId) {
        return repository.findByPurchaseTypeAndPurchaseId(type, purchaseId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminRefundRequestResponse get(UUID id) {
        return toResponse(findOrThrow(id));
    }

    /**
     * Resolving a request gives the money back first, outside any database transaction, and only
     * then marks the request resolved: if Mercado Pago fails the request stays pending to retry.
     */
    public AdminRefundRequestResponse update(
            UUID id, UpdateRefundRequestRequest req, String adminName) {
        if ("RESOLVE".equals(req.action())) {
            RefundRequest pending = findOrThrow(id);
            if (pending.getStatus() != RefundRequestStatus.PENDING) {
                throw new InvalidInputException("Already resolved");
            }
            adminRefundService.refund(pending.getPurchaseType(), pending.getPurchaseId(), true);
        }
        return transactionTemplate.execute(status -> applyUpdate(id, req, adminName));
    }

    private AdminRefundRequestResponse applyUpdate(
            UUID id, UpdateRefundRequestRequest req, String adminName) {
        RefundRequest r = findOrThrow(id);
        String action = req.action();
        if (action != null) {
            switch (action) {
                case "RESOLVE" -> {
                    if (r.getStatus() != RefundRequestStatus.PENDING) {
                        throw new InvalidInputException("Already resolved");
                    }
                    r.setStatus(RefundRequestStatus.RESOLVED);
                    r.setResolvedByName(adminName);
                    r.setResolvedAt(Instant.now());
                }
                case "REOPEN" -> {
                    if (r.getStatus() != RefundRequestStatus.RESOLVED) {
                        throw new InvalidInputException("Not resolved");
                    }
                    r.setStatus(RefundRequestStatus.PENDING);
                    r.setResolvedByName(null);
                    r.setResolvedAt(null);
                }
                default -> throw new InvalidInputException("Unknown action: " + action);
            }
        }
        if (req.note() != null) {
            r.setNote(req.note().isBlank() ? null : req.note().strip());
        }
        repository.save(r);
        return toResponse(r);
    }

    private RefundRequest findOrThrow(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Refund request not found"));
    }

    private AdminRefundRequestResponse toResponse(RefundRequest r) {
        PurchaseSummary summary = loadSummary(r.getPurchaseType(), r.getPurchaseId());
        return new AdminRefundRequestResponse(
                r.getId(),
                r.getPurchaseType(),
                r.getPurchaseId(),
                r.getReason().name(),
                r.getChannel(),
                r.getMessage(),
                r.getNote(),
                r.getStatus().name(),
                r.getResolvedByName(),
                r.getResolvedAt(),
                r.getCreatedAt(),
                r.getRequestedBy().getName() + " " + r.getRequestedBy().getLastName(),
                "@" + r.getRequestedBy().getUsername(),
                r.getRequestedBy().getEmail(),
                summary.productName(),
                summary.productDetail(),
                summary.amount(),
                summary.currency(),
                summary.status(),
                summary.paidAt(),
                false);
    }

    private record PurchaseSummary(
            String productName,
            String productDetail,
            BigDecimal amount,
            String currency,
            String status,
            Instant paidAt) {}

    private PurchaseSummary loadSummary(String type, UUID id) {
        return switch (type) {
            case AdminPurchasesService.TYPE_SEATS ->
                    seatsPurchaseRepository
                            .findById(id)
                            .map(
                                    p ->
                                            new PurchaseSummary(
                                                    p.getCourse().getName(),
                                                    p.getSeats()
                                                            + " cupos × "
                                                            + p.getDurationDays()
                                                            + " días",
                                                    p.getTotalAmount(),
                                                    p.getCurrency(),
                                                    p.getStatus().name(),
                                                    p.getPaidAt()))
                            .orElse(unknown());
            case AdminPurchasesService.TYPE_GEMS ->
                    gemOrderRepository
                            .findById(id)
                            .map(
                                    o ->
                                            new PurchaseSummary(
                                                    o.getGemPack().getProductId(),
                                                    o.getGems() + " gemas",
                                                    o.getAmount(),
                                                    o.getCurrency(),
                                                    gemStatus(o.getStatus().name()),
                                                    o.getPaidAt()))
                            .orElse(unknown());
            case AdminPurchasesService.TYPE_COURSE ->
                    courseOrderRepository
                            .findById(id)
                            .map(
                                    o ->
                                            new PurchaseSummary(
                                                    o.getCourse().getName(),
                                                    "Acceso permanente",
                                                    o.getAmount(),
                                                    o.getCurrency(),
                                                    courseStatus(o.getStatus().name()),
                                                    o.getPaidAt()))
                            .orElse(unknown());
            default -> unknown();
        };
    }

    private static PurchaseSummary unknown() {
        return new PurchaseSummary("—", "—", BigDecimal.ZERO, "ARS", "—", null);
    }

    private static String gemStatus(String s) {
        return switch (s) {
            case "PENDING" -> "PENDING_PAYMENT";
            default -> s;
        };
    }

    private static String courseStatus(String s) {
        return switch (s) {
            case "PENDING" -> "PENDING_PAYMENT";
            default -> s;
        };
    }
}
