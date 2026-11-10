package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.PaymentProviderException;
import com.signasource.signa_api.gamification.dto.GemCheckoutResponse;
import com.signasource.signa_api.gamification.dto.GemOrderResponse;
import com.signasource.signa_api.gamification.dto.GemPackResponse;
import com.signasource.signa_api.gamification.dto.PendingGemOrderResponse;
import com.signasource.signa_api.gamification.entity.GemOrder;
import com.signasource.signa_api.gamification.entity.GemOrderStatus;
import com.signasource.signa_api.gamification.entity.GemPack;
import com.signasource.signa_api.gamification.repository.GemOrderRepository;
import com.signasource.signa_api.gamification.repository.GemPackRepository;
import com.signasource.signa_api.organizations.service.MercadoPagoClient;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.CheckoutOrder;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.OrderRequest;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.Payment;
import com.signasource.signa_api.users.entity.User;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gem packs bought with real money through Mercado Pago Checkout Pro.
 *
 * <p>The app asks for a checkout URL and opens it. Gems are credited only after reading the order
 * back from the Mercado Pago API with our token ({@link GemOrderSettlementService}), never from
 * what the app or the notification says. Two paths get there: the webhook (source of truth) and the
 * app's sync when the buyer comes back. Not transactional: it calls Mercado Pago over HTTP.
 */
@Slf4j
@Service
public class GemOrderService {

    /** How far back an open order can still turn into a payment (cash vouchers last days). */
    static final Duration PENDING_WINDOW = Duration.ofDays(7);

    /** Public page of this API Mercado Pago sends the buyer back to; it hands over to the app. */
    public static final String RETURN_PATH = "/payments/mercado-pago/return";

    private final GemPackRepository packRepository;
    private final GemOrderRepository orderRepository;
    private final GemOrderSettlementService settlement;
    private final PurchaseService purchaseService;
    private final MercadoPagoClient mercadoPago;
    private final String baseUrl;

    public GemOrderService(
            GemPackRepository packRepository,
            GemOrderRepository orderRepository,
            GemOrderSettlementService settlement,
            PurchaseService purchaseService,
            MercadoPagoClient mercadoPago,
            @Value("${app.base-url:}") String baseUrl) {
        this.packRepository = packRepository;
        this.orderRepository = orderRepository;
        this.settlement = settlement;
        this.purchaseService = purchaseService;
        this.mercadoPago = mercadoPago;
        this.baseUrl = baseUrl;
    }

    @Transactional(readOnly = true)
    public List<GemPackResponse> getPacks() {
        return packRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(GemPackResponse::from)
                .toList();
    }

    public GemCheckoutResponse startCheckout(User user, UUID packId) {
        purchaseService.ensureEnabled(user);
        GemPack pack =
                packRepository
                        .findById(packId)
                        .filter(GemPack::isActive)
                        .orElseThrow(() -> new NotFoundException("Gem pack not found"));
        if (pack.getPrice() == null || pack.getPrice().signum() <= 0) {
            throw new InvalidInputException("This gem pack has no price yet");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new PaymentProviderException("app.base-url is required to return from checkout");
        }

        GemOrder order =
                orderRepository.save(
                        GemOrder.builder()
                                .user(user)
                                .gemPack(pack)
                                .gems(pack.getGems())
                                .amount(pack.getPrice())
                                .currency(pack.getCurrency())
                                .build());

        String returnUrl = baseUrl + RETURN_PATH + "?status=";
        CheckoutOrder checkout =
                mercadoPago.createOrder(
                        new OrderRequest(
                                order.reference(),
                                "Pack de " + pack.getGems() + " gemas · Signa",
                                1,
                                pack.getPrice(),
                                pack.getCurrency(),
                                returnUrl + "approved",
                                returnUrl + "rejected",
                                returnUrl + "pending"));
        order.setMercadoPagoOrderId(checkout.id());
        orderRepository.save(order);
        return new GemCheckoutResponse(order.getId(), checkout.checkoutUrl());
    }

    /** The app re-checks an order when the buyer comes back. Only the stored order is read. */
    public GemOrderResponse sync(User user, UUID orderId) {
        GemOrder order =
                orderRepository
                        .findByIdAndUserId(orderId, user.getId())
                        .orElseThrow(() -> new NotFoundException("Gem order not found"));
        String mercadoPagoOrderId = order.getMercadoPagoOrderId();
        if (mercadoPagoOrderId == null || mercadoPagoOrderId.isBlank()) {
            throw new InvalidInputException("This order has no checkout to check yet");
        }
        Payment payment = mercadoPago.getOrder(mercadoPagoOrderId);
        GemOrder settled = settlement.settle(order.getId(), payment);
        return GemOrderResponse.from(
                settled != null ? settled : order, purchaseService.getOrCreateStats(user));
    }

    /**
     * The user's recent orders whose payment is in process at Mercado Pago (cash, transfer,
     * review), so the app can say "your payment is on its way" after the buyer left the checkout.
     * Each candidate is re-read from Mercado Pago and settled on the way (an approved one is
     * credited here), so only orders still unpaid and not abandoned are returned. A Mercado Pago
     * failure on one order just leaves it out.
     */
    public List<PendingGemOrderResponse> pending(User user) {
        List<GemOrder> candidates =
                orderRepository
                        .findTop5ByUserIdAndStatusAndMercadoPagoOrderIdIsNotNullAndCreatedAtAfterOrderByCreatedAtDesc(
                                user.getId(),
                                GemOrderStatus.PENDING,
                                Instant.now().minus(PENDING_WINDOW));
        List<PendingGemOrderResponse> pending = new ArrayList<>();
        for (GemOrder order : candidates) {
            try {
                Payment payment = mercadoPago.getOrder(order.getMercadoPagoOrderId());
                GemOrder settled = settlement.settle(order.getId(), payment);
                GemOrder current = settled != null ? settled : order;
                if (current.getStatus() == GemOrderStatus.PENDING && payment.isInProcess()) {
                    pending.add(PendingGemOrderResponse.from(current));
                }
            } catch (RuntimeException e) {
                log.warn("Could not check gem order {} with Mercado Pago", order.getId(), e);
            }
        }
        return pending;
    }

    /**
     * Applies a Mercado Pago order notified by the webhook; a no-op for what is not a gem order.
     */
    public void applyPayment(Payment payment) {
        parseOrderId(payment.externalReference())
                .ifPresent(orderId -> settlement.settle(orderId, payment));
    }

    /** {@code true} when the Mercado Pago reference belongs to a gem order. */
    public static boolean isGemReference(String externalReference) {
        return externalReference != null && externalReference.startsWith(GemOrder.REFERENCE_PREFIX);
    }

    private static Optional<UUID> parseOrderId(String externalReference) {
        if (!isGemReference(externalReference)) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    UUID.fromString(
                            externalReference.substring(GemOrder.REFERENCE_PREFIX.length())));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
