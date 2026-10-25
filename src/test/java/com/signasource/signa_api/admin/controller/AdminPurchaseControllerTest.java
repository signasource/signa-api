package com.signasource.signa_api.admin.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.admin.dto.AdminPurchaseResponse;
import com.signasource.signa_api.admin.dto.ConfirmPaymentRequest;
import com.signasource.signa_api.organizations.dto.PurchaseDetailResponse;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.service.PurchasePaymentService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminPurchaseControllerTest {

    @Mock private PurchasePaymentService paymentService;

    @InjectMocks private AdminPurchaseController controller;

    @Test
    void shouldListPurchasesByStatus() {
        List<AdminPurchaseResponse> purchases = List.of();
        when(paymentService.listForAdmin(PurchaseStatus.PENDING_PAYMENT)).thenReturn(purchases);

        assertSame(purchases, controller.list(PurchaseStatus.PENDING_PAYMENT).getBody());
    }

    @Test
    void shouldConfirmAPaymentWithOrWithoutReference() {
        UUID purchaseId = UUID.randomUUID();
        PurchaseDetailResponse detail = new PurchaseDetailResponse(null, List.of(), List.of());
        when(paymentService.confirmPayment(purchaseId, "ref")).thenReturn(detail);
        when(paymentService.confirmPayment(purchaseId, null)).thenReturn(detail);

        assertSame(
                detail,
                controller.confirmPayment(purchaseId, new ConfirmPaymentRequest("ref")).getBody());
        assertSame(detail, controller.confirmPayment(purchaseId, null).getBody());
    }
}
