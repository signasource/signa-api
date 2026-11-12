package com.signasource.signa_api.admin.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.admin.dto.AdminPurchaseDetailResponse;
import com.signasource.signa_api.admin.dto.AdminPurchaseListItemResponse;
import com.signasource.signa_api.admin.dto.ConfirmPaymentRequest;
import com.signasource.signa_api.admin.service.AdminPurchasesService;
import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminPurchaseControllerTest {

    @Mock private AdminPurchasesService service;
    @Mock private CustomUserDetails userDetails;
    @Mock private User user;

    @InjectMocks private AdminPurchaseController controller;

    @Test
    void shouldListPurchases() {
        List<AdminPurchaseListItemResponse> purchases = List.of();
        when(service.listAll()).thenReturn(purchases);

        assertSame(purchases, controller.list().getBody());
    }

    @Test
    void shouldConfirmAPaymentWithOrWithoutReference() {
        when(userDetails.getUser()).thenReturn(user);
        when(user.getName()).thenReturn("Admin");
        when(user.getLastName()).thenReturn("Test");
        UUID purchaseId = UUID.randomUUID();
        AdminPurchaseDetailResponse detail =
                new AdminPurchaseDetailResponse(
                        null, null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null,
                        List.of());
        when(service.confirmPayment(
                        eq(purchaseId), nullable(ConfirmPaymentRequest.class), anyString()))
                .thenReturn(detail);

        assertSame(
                detail,
                controller
                        .confirmPayment(
                                purchaseId,
                                new ConfirmPaymentRequest("ref", null, null),
                                userDetails)
                        .getBody());
        assertSame(detail, controller.confirmPayment(purchaseId, null, userDetails).getBody());
    }
}
