package com.signasource.signa_api.admin.service;

import com.signasource.signa_api.admin.dto.AdminRefundRequestResponse;
import com.signasource.signa_api.admin.dto.UpdateRefundRequestRequest;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.repository.GemOrderRepository;
import com.signasource.signa_api.learning.repository.CourseOrderRepository;
import com.signasource.signa_api.organizations.entity.RefundRequest;
import com.signasource.signa_api.organizations.entity.RefundRequestStatus;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.repository.RefundRequestRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminRefundRequestService {

    private final RefundRequestRepository repository;
    private final CoursePurchaseRepository seatsPurchaseRepository;
    private final GemOrderRepository gemOrderRepository;
    private final CourseOrderRepository courseOrderRepository;

    @Transactional(readOnly = true)
    public List<AdminRefundRequestResponse> listAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
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

    @Transactional
    public AdminRefundRequestResponse update(
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
                summary.paidAt());
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
