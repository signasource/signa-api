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
    private final String panelUrl;

    public MercadoPagoCheckoutService(
            CoursePurchaseRepository purchaseRepository,
            OrganizationAccessService accessService,
            PurchasePaymentService paymentService,
            CoursePurchaseService purchaseService,
            MercadoPagoClient mercadoPago,
            MercadoPagoSignatureVerifier signatureVerifier,
            @Value("${app.panel-url:}") String panelUrl) {
        this.purchaseRepository = purchaseRepository;
        this.accessService = accessService;
        this.paymentService = paymentService;
        this.purchaseService = purchaseService;
        this.mercadoPago = mercadoPago;
        this.signatureVerifier = signatureVerifier;
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
     * Credits an approved payment, or gives it back when it cannot pay its purchase. The refund
     * happens after {@link PurchasePaymentService#settle} commits; if Mercado Pago fails, the
     * refund stays pending and the exception (503) makes Mercado Pago retry the notification.
     */
    private void apply(Payment payment) {
        if (payment.isRefunded()) {
            paymentService.markRefunded(payment.id(), null);
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
                paymentService.markRefunded(payment.id(), mercadoPago.refund(payment.id()));
            }
            case UNKNOWN_PURCHASE ->
                    log.warn("Approved Mercado Pago payment {} matches no purchase", payment.id());
            case CONFIRMED, ALREADY_CONFIRMED, ALREADY_REFUNDED -> {}
        }
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
