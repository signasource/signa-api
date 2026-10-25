package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.ForbiddenException;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.PaymentProviderException;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.organizations.dto.CheckoutResponse;
import com.signasource.signa_api.organizations.dto.PaymentSyncResponse;
import com.signasource.signa_api.organizations.dto.PurchaseDetailResponse;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.CheckoutPreference;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.Payment;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.PreferenceRequest;
import com.signasource.signa_api.users.entity.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MercadoPagoCheckoutServiceTest {

    private static final String PANEL = "https://panel.signa.ar";

    @Mock private CoursePurchaseRepository purchaseRepository;
    @Mock private OrganizationAccessService accessService;
    @Mock private PurchasePaymentService paymentService;
    @Mock private CoursePurchaseService purchaseService;
    @Mock private MercadoPagoClient mercadoPago;
    @Mock private MercadoPagoSignatureVerifier signatureVerifier;

    private MercadoPagoCheckoutService service;
    private User actor;
    private Organization organization;
    private CoursePurchase purchase;

    @BeforeEach
    void setUp() {
        service = serviceWithPanel(PANEL);
        actor = new User();
        actor.setId(UUID.randomUUID());
        organization =
                Organization.builder()
                        .id(UUID.randomUUID())
                        .name("Hospital")
                        .status(OrganizationStatus.ACTIVE)
                        .build();
        purchase =
                CoursePurchase.builder()
                        .id(UUID.randomUUID())
                        .organization(organization)
                        .course(Course.builder().id(UUID.randomUUID()).name("LSA Salud").build())
                        .seats(15)
                        .durationDays(30)
                        .pricePerSeat(new BigDecimal("16200.00"))
                        .totalAmount(new BigDecimal("243000.00"))
                        .currency("ARS")
                        .status(PurchaseStatus.PENDING_PAYMENT)
                        .build();
    }

    private MercadoPagoCheckoutService serviceWithPanel(String panelUrl) {
        return new MercadoPagoCheckoutService(
                purchaseRepository,
                accessService,
                paymentService,
                purchaseService,
                mercadoPago,
                signatureVerifier,
                panelUrl);
    }

    private void stubPurchase() {
        when(purchaseRepository.findByIdAndOrganizationId(purchase.getId(), organization.getId()))
                .thenReturn(Optional.of(purchase));
    }

    private Payment payment(String status, String reference, String amount, String currency) {
        return new Payment(
                "987", status, reference, amount == null ? null : new BigDecimal(amount), currency);
    }

    private Payment approved() {
        return payment("approved", purchase.getId().toString(), "243000", "ARS");
    }

    @Test
    void shouldCreateAPreferenceForTheSeatsAndReturnTheCheckoutUrl() {
        stubPurchase();
        when(mercadoPago.createPreference(any()))
                .thenReturn(new CheckoutPreference("pref-1", "https://mp.com/checkout/pref-1"));

        CheckoutResponse response =
                service.startCheckout(actor, organization.getId(), purchase.getId());

        verify(accessService).requireManage(actor, organization.getId());
        assertEquals("https://mp.com/checkout/pref-1", response.checkoutUrl());
        ArgumentCaptor<PreferenceRequest> request =
                ArgumentCaptor.forClass(PreferenceRequest.class);
        verify(mercadoPago).createPreference(request.capture());
        assertEquals(purchase.getId().toString(), request.getValue().externalReference());
        assertEquals(15, request.getValue().quantity());
        assertEquals(new BigDecimal("16200.00"), request.getValue().unitPrice());
        assertEquals("ARS", request.getValue().currency());
        assertEquals("Cupos SIGNA · LSA Salud · 30 días", request.getValue().title());
        assertEquals(
                PANEL + "/dashboard/purchases/" + purchase.getId() + "?pago=aprobado",
                request.getValue().successUrl());
        assertEquals("pref-1", purchase.getMercadoPagoPreferenceId());
        verify(purchaseRepository).save(purchase);
    }

    @Test
    void shouldOnlyCheckOutUnpaidPurchasesOfValidatedOrganizations() {
        stubPurchase();
        purchase.setStatus(PurchaseStatus.PAID);
        assertThrows(
                InvalidInputException.class,
                () -> service.startCheckout(actor, organization.getId(), purchase.getId()));

        purchase.setStatus(PurchaseStatus.PENDING_PAYMENT);
        organization.setStatus(OrganizationStatus.SUSPENDED);
        assertThrows(
                InvalidInputException.class,
                () -> service.startCheckout(actor, organization.getId(), purchase.getId()));
        verifyNoInteractions(mercadoPago);
    }

    @Test
    void shouldRequireThePanelUrlToBuildTheReturnUrls() {
        service = serviceWithPanel("");
        stubPurchase();

        assertThrows(
                PaymentProviderException.class,
                () -> service.startCheckout(actor, organization.getId(), purchase.getId()));
        verifyNoInteractions(mercadoPago);
    }

    @Test
    void shouldNotCheckOutAPurchaseOfAnotherOrganization() {
        when(purchaseRepository.findByIdAndOrganizationId(purchase.getId(), organization.getId()))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> service.startCheckout(actor, organization.getId(), purchase.getId()));
    }

    @Test
    void shouldStopWhenTheActorCannotManageTheOrganization() {
        doThrow(new ForbiddenException("no"))
                .when(accessService)
                .requireManage(actor, organization.getId());

        assertThrows(
                ForbiddenException.class,
                () -> service.startCheckout(actor, organization.getId(), purchase.getId()));
        verifyNoInteractions(mercadoPago);
    }

    @Test
    void shouldConfirmAnApprovedPaymentOnSync() {
        stubPurchase();
        when(mercadoPago.getPayment("987")).thenReturn(approved());
        when(purchaseRepository.findById(purchase.getId())).thenReturn(Optional.of(purchase));
        PurchaseDetailResponse detail = new PurchaseDetailResponse(null, List.of());
        when(purchaseService.get(actor, organization.getId(), purchase.getId())).thenReturn(detail);

        PaymentSyncResponse response =
                service.syncPayment(actor, organization.getId(), purchase.getId(), "987");

        verify(paymentService).confirmPayment(purchase.getId(), "mercadopago:987");
        assertEquals("approved", response.paymentStatus());
        assertSame(detail, response.purchase());
    }

    @Test
    void shouldReportARejectedPaymentOnSyncWithoutConfirming() {
        stubPurchase();
        when(mercadoPago.getPayment("987"))
                .thenReturn(payment("rejected", purchase.getId().toString(), "243000", "ARS"));

        PaymentSyncResponse response =
                service.syncPayment(actor, organization.getId(), purchase.getId(), "987");

        assertEquals("rejected", response.paymentStatus());
        verify(paymentService, never()).confirmPayment(any(), anyString());
    }

    @Test
    void shouldRejectASyncWithThePaymentOfAnotherPurchase() {
        stubPurchase();
        when(mercadoPago.getPayment("987"))
                .thenReturn(payment("approved", UUID.randomUUID().toString(), "243000", "ARS"));

        assertThrows(
                InvalidInputException.class,
                () -> service.syncPayment(actor, organization.getId(), purchase.getId(), "987"));
        verify(paymentService, never()).confirmPayment(any(), anyString());
    }

    @Test
    void shouldConfirmAnApprovedPaymentFromTheWebhook() {
        when(signatureVerifier.isEnabled()).thenReturn(true);
        when(signatureVerifier.isValid("sig", "req", "987")).thenReturn(true);
        when(mercadoPago.getPayment("987")).thenReturn(approved());
        when(purchaseRepository.findById(purchase.getId())).thenReturn(Optional.of(purchase));

        service.handleNotification("payment", "987", "sig", "req");

        verify(paymentService).confirmPayment(purchase.getId(), "mercadopago:987");
    }

    @Test
    void shouldRejectAWebhookWithABadSignature() {
        when(signatureVerifier.isEnabled()).thenReturn(true);
        when(signatureVerifier.isValid("sig", "req", "987")).thenReturn(false);

        assertThrows(
                ForbiddenException.class,
                () -> service.handleNotification("payment", "987", "sig", "req"));
        verifyNoInteractions(mercadoPago);
    }

    @Test
    void shouldIgnoreNotificationsThatAreNotPayments() {
        service.handleNotification("merchant_order", "1", null, null);
        service.handleNotification("payment", " ", null, null);
        service.handleNotification(null, "1", null, null);

        verifyNoInteractions(mercadoPago, paymentService);
    }

    @Test
    void shouldNotConfirmAPaymentThatDoesNotMatchThePurchase() {
        when(purchaseRepository.findById(purchase.getId())).thenReturn(Optional.of(purchase));
        when(mercadoPago.getPayment("1"))
                .thenReturn(payment("approved", purchase.getId().toString(), "100", "ARS"));
        when(mercadoPago.getPayment("2"))
                .thenReturn(payment("approved", purchase.getId().toString(), "243000", "USD"));
        when(mercadoPago.getPayment("3"))
                .thenReturn(payment("approved", purchase.getId().toString(), null, "ARS"));

        service.handleNotification("payment", "1", null, null);
        service.handleNotification("payment", "2", null, null);
        service.handleNotification("payment", "3", null, null);

        verify(paymentService, never()).confirmPayment(any(), anyString());
    }

    @Test
    void shouldIgnoreApprovedPaymentsOfUnknownOrCancelledPurchases() {
        when(mercadoPago.getPayment("1")).thenReturn(payment("approved", "not-a-uuid", "1", "ARS"));
        UUID unknown = UUID.randomUUID();
        when(mercadoPago.getPayment("2"))
                .thenReturn(payment("approved", unknown.toString(), "1", "ARS"));
        when(purchaseRepository.findById(unknown)).thenReturn(Optional.empty());
        purchase.setStatus(PurchaseStatus.CANCELLED);
        when(mercadoPago.getPayment("3")).thenReturn(approved());
        when(purchaseRepository.findById(purchase.getId())).thenReturn(Optional.of(purchase));

        service.handleNotification("payment", "1", null, null);
        service.handleNotification("payment", "2", null, null);
        service.handleNotification("payment", "3", null, null);

        verify(paymentService, never()).confirmPayment(any(), anyString());
    }
}
