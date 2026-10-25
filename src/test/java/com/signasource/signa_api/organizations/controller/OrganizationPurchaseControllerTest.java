package com.signasource.signa_api.organizations.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.organizations.dto.CheckoutResponse;
import com.signasource.signa_api.organizations.dto.CreatePurchaseRequest;
import com.signasource.signa_api.organizations.dto.PaymentSyncResponse;
import com.signasource.signa_api.organizations.dto.PurchaseCatalogResponse;
import com.signasource.signa_api.organizations.dto.PurchaseDetailResponse;
import com.signasource.signa_api.organizations.dto.PurchaseQuoteResponse;
import com.signasource.signa_api.organizations.dto.PurchaseResponse;
import com.signasource.signa_api.organizations.dto.SyncPaymentRequest;
import com.signasource.signa_api.organizations.dto.UpdateSeatEmailRequest;
import com.signasource.signa_api.organizations.service.CoursePurchaseService;
import com.signasource.signa_api.organizations.service.MercadoPagoCheckoutService;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class OrganizationPurchaseControllerTest {

    @Mock private CoursePurchaseService purchaseService;
    @Mock private MercadoPagoCheckoutService checkoutService;

    @InjectMocks private OrganizationPurchaseController controller;

    private User user;
    private CustomUserDetails principal;
    private UUID organizationId;
    private UUID purchaseId;
    private UUID seatId;
    private PurchaseDetailResponse detail;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        principal = mock(CustomUserDetails.class);
        organizationId = UUID.randomUUID();
        purchaseId = UUID.randomUUID();
        seatId = UUID.randomUUID();
        detail = new PurchaseDetailResponse(null, List.of());
    }

    @Test
    void shouldReturnTheCatalog() {
        PurchaseCatalogResponse catalog =
                new PurchaseCatalogResponse(List.of(), "ARS", List.of(), 15, 60, 10, 500);
        when(purchaseService.catalog()).thenReturn(catalog);

        assertSame(catalog, controller.getCatalog().getBody());
    }

    @Test
    void shouldReturnAQuote() {
        UUID courseId = UUID.randomUUID();
        PurchaseQuoteResponse quote =
                new PurchaseQuoteResponse(
                        courseId, "LSA", 1, 2, 30, null, 0, null, null, null, null, "ARS");
        when(purchaseService.quote(courseId, 2, 30)).thenReturn(quote);

        assertSame(quote, controller.quote(courseId, 2, 30).getBody());
    }

    @Test
    void shouldListPurchases() {
        when(principal.getUser()).thenReturn(user);
        List<PurchaseResponse> purchases = List.of();
        when(purchaseService.list(user, organizationId)).thenReturn(purchases);

        assertSame(purchases, controller.listPurchases(organizationId, principal).getBody());
    }

    @Test
    void shouldReturn201WhenCreatingAPurchase() {
        when(principal.getUser()).thenReturn(user);
        CreatePurchaseRequest request =
                new CreatePurchaseRequest(UUID.randomUUID(), 30, List.of("a@x.com"));
        when(purchaseService.create(user, organizationId, request)).thenReturn(detail);

        ResponseEntity<PurchaseDetailResponse> result =
                controller.createPurchase(organizationId, request, principal);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertSame(detail, result.getBody());
    }

    @Test
    void shouldReturnAPurchase() {
        when(principal.getUser()).thenReturn(user);
        when(purchaseService.get(user, organizationId, purchaseId)).thenReturn(detail);

        assertSame(detail, controller.getPurchase(organizationId, purchaseId, principal).getBody());
    }

    @Test
    void shouldCancelAPurchase() {
        when(principal.getUser()).thenReturn(user);
        when(purchaseService.cancel(user, organizationId, purchaseId)).thenReturn(detail);

        assertSame(
                detail, controller.cancelPurchase(organizationId, purchaseId, principal).getBody());
    }

    @Test
    void shouldUpdateASeatEmail() {
        when(principal.getUser()).thenReturn(user);
        UpdateSeatEmailRequest request = new UpdateSeatEmailRequest("b@x.com");
        when(purchaseService.updateSeatEmail(user, organizationId, purchaseId, seatId, request))
                .thenReturn(detail);

        assertSame(
                detail,
                controller
                        .updateSeatEmail(organizationId, purchaseId, seatId, request, principal)
                        .getBody());
    }

    @Test
    void shouldRemindASeat() {
        when(principal.getUser()).thenReturn(user);
        when(purchaseService.remindSeat(user, organizationId, purchaseId, seatId))
                .thenReturn(detail);

        assertSame(
                detail,
                controller.remindSeat(organizationId, purchaseId, seatId, principal).getBody());
    }

    @Test
    void shouldStartTheCheckout() {
        when(principal.getUser()).thenReturn(user);
        CheckoutResponse checkout = new CheckoutResponse("https://mp/checkout");
        when(checkoutService.startCheckout(user, organizationId, purchaseId)).thenReturn(checkout);

        assertSame(
                checkout,
                controller.startCheckout(organizationId, purchaseId, principal).getBody());
    }

    @Test
    void shouldSyncAPayment() {
        when(principal.getUser()).thenReturn(user);
        PaymentSyncResponse synced = new PaymentSyncResponse("approved", detail);
        when(checkoutService.syncPayment(user, organizationId, purchaseId, "987"))
                .thenReturn(synced);

        assertSame(
                synced,
                controller
                        .syncPayment(
                                organizationId,
                                purchaseId,
                                new SyncPaymentRequest("987"),
                                principal)
                        .getBody());
    }
}
