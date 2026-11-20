package com.signasource.signa_api.admin.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.PaymentProviderException;
import com.signasource.signa_api.gamification.entity.GemOrder;
import com.signasource.signa_api.gamification.entity.GemOrderStatus;
import com.signasource.signa_api.gamification.repository.GemOrderRepository;
import com.signasource.signa_api.gamification.service.GemOrderSettlementService;
import com.signasource.signa_api.learning.entity.CourseOrder;
import com.signasource.signa_api.learning.entity.CourseOrderStatus;
import com.signasource.signa_api.learning.repository.CourseOrderRepository;
import com.signasource.signa_api.learning.service.CourseOrderSettlementService;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.service.MercadoPagoClient;
import com.signasource.signa_api.organizations.service.PurchasePaymentService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Refunds a purchase on an admin's behalf the way the automatic withdrawal does: the money goes
 * back through Mercado Pago first and the purchase is only revoked once that succeeded. If Mercado
 * Pago fails nothing is revoked and the admin can retry. Deliberately not transactional: the
 * Mercado Pago call must stay outside any database transaction.
 */
@Service
@RequiredArgsConstructor
public class AdminRefundService {

    private final CoursePurchaseRepository seatsPurchaseRepository;
    private final GemOrderRepository gemOrderRepository;
    private final CourseOrderRepository courseOrderRepository;
    private final PurchasePaymentService purchasePaymentService;
    private final GemOrderSettlementService gemOrderSettlementService;
    private final CourseOrderSettlementService courseOrderSettlementService;
    private final MercadoPagoClient mercadoPagoClient;

    /**
     * Refunds a purchase. When {@code lenient}, a purchase that is missing or not paid is skipped
     * instead of failing (resolving a request whose purchase was already refunded).
     */
    public void refund(String type, UUID id, boolean lenient) {
        switch (type) {
            case AdminPurchasesService.TYPE_SEATS -> refundSeats(id, lenient);
            case AdminPurchasesService.TYPE_GEMS -> refundGems(id, lenient);
            case AdminPurchasesService.TYPE_COURSE -> refundCourse(id, lenient);
            default -> throw new NotFoundException("Unknown purchase type: " + type);
        }
    }

    private void refundSeats(UUID id, boolean lenient) {
        if (lenient
                && seatsPurchaseRepository
                        .findById(id)
                        .filter(p -> p.getStatus() == PurchaseStatus.PAID)
                        .isEmpty()) {
            return;
        }
        String paymentId = purchasePaymentService.startAdminRefund(id);
        if (paymentId == null) {
            purchasePaymentService.reverseManual(id);
            return;
        }
        purchasePaymentService.markRefunded(paymentId, giveMoneyBack(paymentId));
    }

    private void refundGems(UUID id, boolean lenient) {
        GemOrder order = gemOrderRepository.findById(id).orElse(null);
        if (order == null) {
            if (lenient) {
                return;
            }
            throw new NotFoundException("Gem order not found");
        }
        if (order.getStatus() != GemOrderStatus.PAID) {
            if (lenient) {
                return;
            }
            throw new InvalidInputException("Only a paid gem order can be refunded");
        }
        giveMoneyBack(order.getMercadoPagoOrderId());
        gemOrderSettlementService.reverseManual(id);
    }

    private void refundCourse(UUID id, boolean lenient) {
        CourseOrder order = courseOrderRepository.findById(id).orElse(null);
        if (order == null) {
            if (lenient) {
                return;
            }
            throw new NotFoundException("Course order not found");
        }
        if (order.getStatus() != CourseOrderStatus.PAID) {
            if (lenient) {
                return;
            }
            throw new InvalidInputException("Only a paid course order can be refunded");
        }
        giveMoneyBack(order.getMercadoPagoOrderId());
        courseOrderSettlementService.reverseManual(id);
    }

    /**
     * Refunds the Mercado Pago payment and returns the refund id (null when there is no payment or
     * someone already gave the money back, which counts as done).
     */
    private String giveMoneyBack(String mercadoPagoOrderId) {
        if (mercadoPagoOrderId == null) {
            return null;
        }
        try {
            return mercadoPagoClient.refund(mercadoPagoOrderId);
        } catch (PaymentProviderException e) {
            if (!mercadoPagoClient.getOrder(mercadoPagoOrderId).isRefunded()) {
                throw e;
            }
            return null;
        }
    }
}
