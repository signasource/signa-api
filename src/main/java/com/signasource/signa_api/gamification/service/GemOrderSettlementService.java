package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.gamification.entity.GemOrder;
import com.signasource.signa_api.gamification.entity.GemOrderStatus;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.GemOrderRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.Payment;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database half of paying a gem order, split from {@link GemOrderService} so the Mercado Pago
 * calls happen outside the transaction. The order row is locked, so the webhook and the app's sync
 * racing each other credit the gems once.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GemOrderSettlementService {

    private final GemOrderRepository orderRepository;
    private final UserStatsRepository userStatsRepository;
    private final PurchaseService purchaseService;

    /** Applies what Mercado Pago says about the payment of an order. Returns the order, if any. */
    @Transactional
    public GemOrder settle(UUID orderId, Payment payment) {
        GemOrder order = orderRepository.findForUpdate(orderId).orElse(null);
        if (order == null) {
            log.warn("Mercado Pago payment {} matches no gem order", payment.id());
            return null;
        }
        if (!payment.id().equals(order.getMercadoPagoOrderId())) {
            log.warn(
                    "Mercado Pago payment {} is not the one of gem order {}",
                    payment.id(),
                    orderId);
            return order;
        }
        if (payment.isRefunded() || payment.isChargedBack()) {
            reverse(order);
        } else if (payment.isApproved() && order.getStatus() == GemOrderStatus.PENDING) {
            credit(order, payment);
        }
        return order;
    }

    private void credit(GemOrder order, Payment payment) {
        boolean sameAmount =
                payment.transactionAmount() != null
                        && payment.transactionAmount().compareTo(order.getAmount()) == 0;
        boolean sameCurrency = order.getCurrency().equalsIgnoreCase(payment.currency());
        if (!sameAmount || !sameCurrency) {
            log.error(
                    "Gem order {} not credited: paid {} {} but costs {} {}",
                    order.getId(),
                    payment.transactionAmount(),
                    payment.currency(),
                    order.getAmount(),
                    order.getCurrency());
            return;
        }
        UserStats stats = purchaseService.getOrCreateStats(order.getUser());
        stats.setGems(stats.getGems() + order.getGems());
        stats.setUpdatedAt(Instant.now());
        userStatsRepository.save(stats);
        order.setStatus(GemOrderStatus.PAID);
        order.setPaidAt(Instant.now());
        orderRepository.save(order);
    }

    /** The money went back: take the gems back too, as far as the user still has them. */
    private void reverse(GemOrder order) {
        if (order.getStatus() != GemOrderStatus.PAID) {
            return;
        }
        UserStats stats = purchaseService.getOrCreateStats(order.getUser());
        stats.setGems(Math.max(0, stats.getGems() - order.getGems()));
        stats.setUpdatedAt(Instant.now());
        userStatsRepository.save(stats);
        order.setStatus(GemOrderStatus.REFUNDED);
        order.setRefundedAt(Instant.now());
        orderRepository.save(order);
        log.warn("Gem order {} refunded or charged back: gems taken back", order.getId());
    }
}
