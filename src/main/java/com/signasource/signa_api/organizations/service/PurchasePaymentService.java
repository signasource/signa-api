package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.admin.dto.AdminPurchaseResponse;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.organizations.dto.PurchaseDetailResponse;
import com.signasource.signa_api.organizations.dto.PurchaseResponse;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PurchasePaymentService {

    static final String MERCADO_PAGO_REFERENCE_PREFIX = "mercadopago:";

    private final CoursePurchaseRepository purchaseRepository;
    private final PurchaseSeatRepository seatRepository;
    private final PurchaseRefundRepository refundRepository;
    private final OrganizationCourseRepository organizationCourseRepository;
    private final SeatInvitationService invitationService;
    private final CoursePurchaseService purchaseService;
    private final PurchasePolicy policy;

    @Transactional
    public PurchaseDetailResponse confirmPayment(UUID purchaseId, String paymentReference) {
        CoursePurchase purchase =
                purchaseRepository
                        .findForUpdateById(purchaseId)
                        .orElseThrow(() -> new NotFoundException("Purchase not found"));
        if (purchase.getStatus() == PurchaseStatus.PAID) {
            return purchaseService.describe(purchase);
        }
        if (purchase.getStatus() == PurchaseStatus.CANCELLED) {
            throw new InvalidInputException("Purchase was cancelled");
        }
        confirm(purchase, paymentReference);
        return purchaseService.describe(purchase);
    }

    /** What {@link #settle} did with an approved payment. */
    public enum Settlement {
        /** The payment paid the purchase. */
        CONFIRMED,
        /** The purchase was already paid by this same payment (repeated notification). */
        ALREADY_CONFIRMED,
        /** A refund was recorded as pending: the caller must refund it in Mercado Pago. */
        REFUND,
        /** The payment was already refunded. */
        ALREADY_REFUNDED,
        /** No purchase with that id. */
        UNKNOWN_PURCHASE
    }

    /**
     * Applies an approved Mercado Pago payment under a lock on the purchase. A payment that cannot
     * pay it (cancelled, already paid by another payment, another amount) is recorded as a pending
     * refund instead: money is never kept for seats that will not be delivered.
     */
    @Transactional
    public Settlement settle(
            UUID purchaseId, String paymentId, BigDecimal amount, String currency) {
        Optional<PurchaseRefund> refund = refundRepository.findByPaymentId(paymentId);
        if (refund.isPresent()) {
            return refund.get().getStatus() == RefundStatus.REFUNDED
                    ? Settlement.ALREADY_REFUNDED
                    : Settlement.REFUND;
        }
        Optional<CoursePurchase> found = purchaseRepository.findForUpdateById(purchaseId);
        if (found.isEmpty()) {
            return Settlement.UNKNOWN_PURCHASE;
        }
        CoursePurchase purchase = found.get();
        String reference = MERCADO_PAGO_REFERENCE_PREFIX + paymentId;

        RefundReason reason;
        if (amount == null
                || amount.compareTo(purchase.getTotalAmount()) != 0
                || !purchase.getCurrency().equals(currency)) {
            reason = RefundReason.AMOUNT_MISMATCH;
        } else if (purchase.getStatus() == PurchaseStatus.CANCELLED) {
            reason = RefundReason.PURCHASE_CANCELLED;
        } else if (purchase.getStatus() == PurchaseStatus.PAID) {
            if (reference.equals(purchase.getPaymentReference())) {
                return Settlement.ALREADY_CONFIRMED;
            }
            reason = RefundReason.ALREADY_PAID;
        } else {
            confirm(purchase, reference);
            return Settlement.CONFIRMED;
        }

        refundRepository.save(
                PurchaseRefund.builder()
                        .purchase(purchase)
                        .paymentId(paymentId)
                        .amount(amount == null ? BigDecimal.ZERO : amount)
                        .currency(
                                currency == null || currency.isBlank()
                                        ? purchase.getCurrency()
                                        : currency)
                        .reason(reason)
                        .build());
        return Settlement.REFUND;
    }

    /** Marks a pending refund as done; does nothing for a payment that was never to be refunded. */
    @Transactional
    public void markRefunded(String paymentId, String mercadoPagoRefundId) {
        refundRepository
                .findByPaymentId(paymentId)
                .filter(refund -> refund.getStatus() == RefundStatus.PENDING)
                .ifPresent(
                        refund -> {
                            refund.setStatus(RefundStatus.REFUNDED);
                            refund.setRefundedAt(Instant.now());
                            if (mercadoPagoRefundId != null) {
                                refund.setMercadoPagoRefundId(mercadoPagoRefundId);
                            }
                            refundRepository.save(refund);
                        });
    }

    private void confirm(CoursePurchase purchase, String paymentReference) {
        UUID purchaseId = purchase.getId();
        Instant now = Instant.now();
        purchase.setStatus(PurchaseStatus.PAID);
        purchase.setPaymentReference(paymentReference);
        purchase.setPaidAt(now);
        purchase.setInvitationDeadline(now.plus(policy.invitationWindow()));
        purchaseRepository.save(purchase);

        UUID organizationId = purchase.getOrganization().getId();
        UUID courseId = purchase.getCourse().getId();
        if (!organizationCourseRepository.existsByOrganizationIdAndCourseId(
                organizationId, courseId)) {
            organizationCourseRepository.save(
                    OrganizationCourse.builder()
                            .organization(purchase.getOrganization())
                            .course(purchase.getCourse())
                            .build());
        }

        for (PurchaseSeat seat : seatRepository.findByPurchaseIdOrderByEmailAsc(purchaseId)) {
            invitationService.invite(seat, now);
        }
    }

    @Transactional(readOnly = true)
    public List<AdminPurchaseResponse> listForAdmin(PurchaseStatus status) {
        List<CoursePurchase> purchases =
                status == null
                        ? purchaseRepository.findAllByOrderByCreatedAtDesc()
                        : purchaseRepository.findByStatusOrderByCreatedAtAsc(status);
        Instant now = Instant.now();
        return purchases.stream()
                .map(
                        purchase ->
                                new AdminPurchaseResponse(
                                        purchase.getOrganization().getId(),
                                        purchase.getOrganization().getName(),
                                        purchase.getPaymentReference(),
                                        PurchaseResponse.from(
                                                purchase,
                                                seatRepository.findByPurchaseIdOrderByEmailAsc(
                                                        purchase.getId()),
                                                now)))
                .toList();
    }
}
