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
import com.signasource.signa_api.users.entity.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The money side of a purchase: confirming its payment, refunding payments that cannot pay it, the
 * organization's withdrawal, and refunds or chargebacks that undo it. Every method locks the
 * purchase row, so the webhook, the panel and the back-office never act on it at the same time.
 * Calls to Mercado Pago happen outside these transactions ({@link MercadoPagoCheckoutService}).
 */
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
    private final PurchaseRevocationService revocationService;
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
        if (purchase.getStatus() != PurchaseStatus.PENDING_PAYMENT) {
            throw new InvalidInputException("Purchase was cancelled or refunded");
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
     * Applies an approved Mercado Pago payment. A payment that cannot pay its purchase (cancelled
     * or refunded, already paid by another payment, another amount) is recorded as a pending refund
     * instead: money is never kept for seats that will not be delivered.
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
        } else if (purchase.getStatus() == PurchaseStatus.CANCELLED
                || purchase.getStatus() == PurchaseStatus.REFUNDED) {
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

    /**
     * Starts the organization's withdrawal: checks the purchase can still be cancelled and records
     * a pending refund of its whole payment. Returns the Mercado Pago payment to refund. A
     * withdrawal already started (Mercado Pago failed last time) is resumed even if the period
     * ended since.
     */
    @Transactional
    public String startWithdrawal(
            UUID organizationId, UUID purchaseId, User actor, String note, Instant now) {
        CoursePurchase purchase =
                purchaseRepository
                        .findForUpdateById(purchaseId)
                        .filter(p -> p.getOrganization().getId().equals(organizationId))
                        .orElseThrow(() -> new NotFoundException("Purchase not found"));
        String paymentId = mercadoPagoPaymentId(purchase);
        Optional<PurchaseRefund> existing =
                paymentId == null ? Optional.empty() : refundRepository.findByPaymentId(paymentId);
        if (existing.isPresent()
                && existing.get().getStatus() == RefundStatus.PENDING
                && existing.get().getReason() == RefundReason.BUYER_REQUESTED) {
            return paymentId;
        }
        if (purchase.getStatus() != PurchaseStatus.PAID) {
            throw new InvalidInputException("Only a paid purchase can be cancelled for a refund");
        }
        if (paymentId == null || purchase.getRefundableUntil() == null) {
            throw new InvalidInputException(
                    "This purchase was not paid with Mercado Pago and cannot be refunded here");
        }
        if (!now.isBefore(purchase.getRefundableUntil())) {
            throw new InvalidInputException("The withdrawal period for this purchase has ended");
        }
        refundRepository.save(
                PurchaseRefund.builder()
                        .purchase(purchase)
                        .paymentId(paymentId)
                        .amount(purchase.getTotalAmount())
                        .currency(purchase.getCurrency())
                        .reason(RefundReason.BUYER_REQUESTED)
                        .note(note == null || note.isBlank() ? null : note.strip())
                        .requestedBy(actor)
                        .build());
        return paymentId;
    }

    /**
     * Marks a pending refund as done once Mercado Pago confirmed it. When the refunded payment had
     * paid its purchase (a withdrawal), the purchase is revoked in the same transaction.
     */
    @Transactional
    public void markRefunded(String paymentId, String mercadoPagoRefundId) {
        Optional<PurchaseRefund> found =
                refundRepository
                        .findByPaymentId(paymentId)
                        .filter(refund -> refund.getStatus() == RefundStatus.PENDING);
        if (found.isEmpty()) {
            return;
        }
        PurchaseRefund refund = found.get();
        Instant now = Instant.now();
        refund.setStatus(RefundStatus.REFUNDED);
        refund.setRefundedAt(now);
        if (mercadoPagoRefundId != null && !mercadoPagoRefundId.isBlank()) {
            refund.setMercadoPagoRefundId(mercadoPagoRefundId);
        }
        refundRepository.save(refund);
        if (refund.getReason().revokesPurchase()) {
            purchaseRepository
                    .findForUpdateById(refund.getPurchase().getId())
                    .filter(purchase -> purchase.getStatus() == PurchaseStatus.PAID)
                    .ifPresent(
                            purchase ->
                                    revocationService.revoke(
                                            purchase, refund.getReason(), refund.getAmount(), now));
        }
    }

    /** What {@link #reverse} did with a payment Mercado Pago reports as given back. */
    public enum Reversal {
        /** It undid the purchase it had paid. */
        PURCHASE_REVOKED,
        /** It was a refund Signa had started; now recorded as done. */
        REFUND_COMPLETED,
        /** Nothing to do: not a payment of a paid purchase, or already handled. */
        IGNORED
    }

    /**
     * Handles a payment Mercado Pago reports as refunded or charged back. If Signa started that
     * refund, it is completed; otherwise, when the payment had paid a purchase, the money is gone
     * and the purchase is revoked, whatever the withdrawal period says.
     */
    @Transactional
    public Reversal reverse(String paymentId, boolean chargeback, BigDecimal amount) {
        Optional<PurchaseRefund> started = refundRepository.findByPaymentId(paymentId);
        if (started.isPresent()) {
            if (started.get().getStatus() == RefundStatus.REFUNDED) {
                return Reversal.IGNORED;
            }
            markRefunded(paymentId, null);
            return Reversal.REFUND_COMPLETED;
        }
        Optional<CoursePurchase> found =
                purchaseRepository
                        .findForUpdateByPaymentReference(MERCADO_PAGO_REFERENCE_PREFIX + paymentId)
                        .filter(purchase -> purchase.getStatus() == PurchaseStatus.PAID);
        if (found.isEmpty()) {
            return Reversal.IGNORED;
        }
        CoursePurchase purchase = found.get();
        Instant now = Instant.now();
        RefundReason reason = chargeback ? RefundReason.CHARGEBACK : RefundReason.EXTERNAL_REFUND;
        BigDecimal refunded = amount == null ? purchase.getTotalAmount() : amount;
        refundRepository.save(
                PurchaseRefund.builder()
                        .purchase(purchase)
                        .paymentId(paymentId)
                        .amount(refunded)
                        .currency(purchase.getCurrency())
                        .reason(reason)
                        .status(RefundStatus.REFUNDED)
                        .refundedAt(now)
                        .build());
        revocationService.revoke(purchase, reason, refunded, now);
        return Reversal.PURCHASE_REVOKED;
    }

    /** The purchase a Mercado Pago payment paid, if it paid one. */
    @Transactional(readOnly = true)
    public Optional<CoursePurchase> findPaidBy(String paymentId) {
        return purchaseRepository
                .findByPaymentReference(MERCADO_PAGO_REFERENCE_PREFIX + paymentId)
                .filter(purchase -> purchase.getStatus() == PurchaseStatus.PAID);
    }

    private static String mercadoPagoPaymentId(CoursePurchase purchase) {
        String reference = purchase.getPaymentReference();
        return reference != null && reference.startsWith(MERCADO_PAGO_REFERENCE_PREFIX)
                ? reference.substring(MERCADO_PAGO_REFERENCE_PREFIX.length())
                : null;
    }

    private void confirm(CoursePurchase purchase, String paymentReference) {
        UUID purchaseId = purchase.getId();
        Instant now = Instant.now();
        purchase.setStatus(PurchaseStatus.PAID);
        purchase.setPaymentReference(paymentReference);
        purchase.setPaidAt(now);
        purchase.setInvitationDeadline(now.plus(policy.invitationWindow()));
        // Only a Mercado Pago payment can be refunded from the panel.
        purchase.setRefundableUntil(
                paymentReference != null
                                && paymentReference.startsWith(MERCADO_PAGO_REFERENCE_PREFIX)
                        ? now.plus(policy.withdrawalWindow())
                        : null);
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
