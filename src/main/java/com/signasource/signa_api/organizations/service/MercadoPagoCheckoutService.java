package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.exceptions.ForbiddenException;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.PaymentProviderException;
import com.signasource.signa_api.organizations.dto.CheckoutResponse;
import com.signasource.signa_api.organizations.dto.PaymentSyncResponse;
import com.signasource.signa_api.organizations.dto.PurchaseDetailResponse;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.CheckoutPreference;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.Payment;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.PreferenceRequest;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Pays seat purchases with Mercado Pago Checkout Pro.
 *
 * <p>The panel asks for a checkout URL and sends the admin there. A payment is credited only after
 * reading it back from the Mercado Pago API with our token: an approved payment whose {@code
 * external_reference} is the purchase and whose amount and currency match its total confirms the
 * purchase through {@link PurchasePaymentService#confirmPayment}, which is idempotent. Two paths
 * get there: the webhook (source of truth) and the panel's sync on return, so the admin does not
 * wait for the notification. No method here is transactional: they call Mercado Pago over HTTP.
 */
@Slf4j
@Service
public class MercadoPagoCheckoutService {

    private static final String PURCHASE_PATH = "/dashboard/purchases/";

    private final CoursePurchaseRepository purchaseRepository;
    private final OrganizationAccessService accessService;
    private final PurchasePaymentService paymentService;
    private final CoursePurchaseService purchaseService;
    private final MercadoPagoClient mercadoPago;
    private final MercadoPagoSignatureVerifier signatureVerifier;
    private final PurchaseNotificationService notifications;
    private final String panelUrl;

    public MercadoPagoCheckoutService(
            CoursePurchaseRepository purchaseRepository,
            OrganizationAccessService accessService,
            PurchasePaymentService paymentService,
            CoursePurchaseService purchaseService,
            MercadoPagoClient mercadoPago,
            MercadoPagoSignatureVerifier signatureVerifier,
            PurchaseNotificationService notifications,
            @Value("${app.panel-url:}") String panelUrl) {
        this.purchaseRepository = purchaseRepository;
        this.accessService = accessService;
        this.paymentService = paymentService;
        this.purchaseService = purchaseService;
        this.mercadoPago = mercadoPago;
        this.signatureVerifier = signatureVerifier;
        this.notifications = notifications;
        this.panelUrl = panelUrl;
    }

    public CheckoutResponse startCheckout(User actor, UUID organizationId, UUID purchaseId) {
        accessService.requireManage(actor, organizationId);
        CoursePurchase purchase = findPurchase(organizationId, purchaseId);
        if (purchase.getStatus() != PurchaseStatus.PENDING_PAYMENT) {
            throw new InvalidInputException("Only unpaid purchases can be paid");
        }
        if (purchase.getOrganization().getStatus() != OrganizationStatus.ACTIVE) {
            throw new InvalidInputException("The organization must be validated before buying");
        }
        if (panelUrl == null || panelUrl.isBlank()) {
            throw new PaymentProviderException("app.panel-url is required to return from checkout");
        }

        String returnUrl = panelUrl + PURCHASE_PATH + purchase.getId();
        CheckoutPreference preference =
                mercadoPago.createPreference(
                        new PreferenceRequest(
                                purchase.getId().toString(),
                                "Cupos SIGNA · "
                                        + purchase.getCourse().getName()
                                        + " · "
                                        + purchase.getDurationDays()
                                        + " días",
                                purchase.getSeats(),
                                purchase.getPricePerSeat(),
                                purchase.getCurrency(),
                                returnUrl + "?pago=aprobado",
                                returnUrl + "?pago=rechazado",
                                returnUrl + "?pago=pendiente"));
        purchase.setMercadoPagoPreferenceId(preference.id());
        purchaseRepository.save(purchase);
        return new CheckoutResponse(preference.checkoutUrl());
    }

    /**
     * Cancels an unpaid purchase and expires its checkout so it cannot be paid anymore. Expiring is
     * best effort: a payment that still gets through is refunded when it is notified.
     */
    public PurchaseDetailResponse cancel(User actor, UUID organizationId, UUID purchaseId) {
        PurchaseDetailResponse cancelled =
                purchaseService.cancel(actor, organizationId, purchaseId);
        String preferenceId = findPurchase(organizationId, purchaseId).getMercadoPagoPreferenceId();
        if (preferenceId != null) {
            try {
                mercadoPago.expirePreference(preferenceId);
            } catch (RuntimeException e) {
                log.warn("Could not expire Mercado Pago preference {}", preferenceId, e);
            }
        }
        return cancelled;
    }

    /**
     * The organization cancels a paid purchase within the withdrawal period. The refund is recorded
     * first, then made in Mercado Pago, and only then the purchase is revoked, so a failure in
     * between leaves a pending refund that a retry (or Mercado Pago's own notification) completes,
     * never a revoked purchase that was not refunded.
     */
    public PurchaseDetailResponse withdraw(
            User actor, UUID organizationId, UUID purchaseId, String reason) {
        accessService.requireManage(actor, organizationId);
        String paymentId =
                paymentService.startWithdrawal(
                        organizationId, purchaseId, actor, reason, Instant.now());
        refund(paymentId);
        return purchaseService.get(actor, organizationId, purchaseId);
    }

    /** The panel reports the {@code payment_id} Mercado Pago appended to the return URL. */
    public PaymentSyncResponse syncPayment(
            User actor, UUID organizationId, UUID purchaseId, String paymentId) {
        accessService.requireManage(actor, organizationId);
        CoursePurchase purchase = findPurchase(organizationId, purchaseId);
        Payment payment = mercadoPago.getPayment(paymentId);
        if (!purchase.getId().toString().equals(payment.externalReference())) {
            throw new InvalidInputException("That payment belongs to another purchase");
        }
        apply(payment);
        return new PaymentSyncResponse(
                payment.status(), purchaseService.get(actor, organizationId, purchaseId));
    }

    /**
     * Handles a webhook. Anything that is not a payment notification is acknowledged and ignored,
     * so Mercado Pago does not retry it.
     */
    public void handleNotification(
            String type, String dataId, String signatureHeader, String requestId) {
        if (signatureVerifier.isEnabled()
                && !signatureVerifier.isValid(signatureHeader, requestId, dataId)) {
            throw new ForbiddenException("Invalid Mercado Pago signature");
        }
        if (!"payment".equals(type) || dataId == null || dataId.isBlank()) {
            return;
        }
        apply(mercadoPago.getPayment(dataId));
    }

    /**
     * Applies what Mercado Pago says about a payment. Approved: credit it, or give it back when it
     * cannot pay its purchase. Refunded or charged back: complete a refund Signa started, or revoke
     * the purchase it had paid. A dispute or a partial refund needs a person, so it only alerts.
     * Refunds run after {@link PurchasePaymentService} commits; if Mercado Pago fails, the refund
     * stays pending and the exception (503) makes Mercado Pago retry the notification.
     */
    private void apply(Payment payment) {
        if (payment.isRefunded() || payment.isChargedBack()) {
            paymentService.reverse(
                    payment.id(), payment.isChargedBack(), payment.transactionAmount());
            return;
        }
        if (payment.isInMediation()) {
            alertAboutPaidPurchase(
                    payment,
                    "Disputa abierta en Mercado Pago",
                    "El comprador abrió un reclamo o contracargo. Si lo gana, la compra se anula"
                            + " sola; mientras tanto no cambia nada.");
            return;
        }
        if (payment.isPartiallyRefunded()) {
            alertAboutPaidPurchase(
                    payment,
                    "Reembolso parcial en Mercado Pago",
                    "Se devolvió parte del pago desde Mercado Pago. Signa no sabe qué cupos"
                            + " corresponden: la compra sigue activa y hay que resolverlo a mano.");
            return;
        }
        if (!payment.isApproved()) {
            return;
        }
        Optional<UUID> purchaseId = parseUuid(payment.externalReference());
        if (purchaseId.isEmpty()) {
            log.warn("Approved Mercado Pago payment {} matches no purchase", payment.id());
            return;
        }
        PurchasePaymentService.Settlement settlement =
                paymentService.settle(
                        purchaseId.get(),
                        payment.id(),
                        payment.transactionAmount(),
                        payment.currency());
        switch (settlement) {
            case REFUND -> {
                log.warn(
                        "Refunding Mercado Pago payment {}: it cannot pay purchase {}",
                        payment.id(),
                        purchaseId.get());
                refund(payment.id());
            }
            case UNKNOWN_PURCHASE ->
                    log.warn("Approved Mercado Pago payment {} matches no purchase", payment.id());
            case CONFIRMED, ALREADY_CONFIRMED, ALREADY_REFUNDED -> {}
        }
    }

    /**
     * Refunds a payment whose refund is recorded as pending and marks it done. If Mercado Pago
     * rejects the refund because someone already gave the money back, that counts as done too.
     */
    private void refund(String paymentId) {
        String refundId;
        try {
            refundId = mercadoPago.refund(paymentId);
        } catch (PaymentProviderException e) {
            if (!mercadoPago.getPayment(paymentId).isRefunded()) {
                throw e;
            }
            refundId = null;
        }
        paymentService.markRefunded(paymentId, refundId);
    }

    private void alertAboutPaidPurchase(Payment payment, String subject, String detail) {
        paymentService
                .findPaidBy(payment.id())
                .ifPresent(
                        purchase ->
                                notifications.alertOps(
                                        subject + ": " + purchase.getOrganization().getName(),
                                        List.of(
                                                detail,
                                                "Organización: "
                                                        + purchase.getOrganization().getName(),
                                                "Curso: " + purchase.getCourse().getName(),
                                                "Compra: " + purchase.getId(),
                                                "Pago: " + payment.id())));
    }

    private static Optional<UUID> parseUuid(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private CoursePurchase findPurchase(UUID organizationId, UUID purchaseId) {
        return purchaseRepository
                .findByIdAndOrganizationId(purchaseId, organizationId)
                .orElseThrow(() -> new NotFoundException("Purchase not found"));
    }
}
