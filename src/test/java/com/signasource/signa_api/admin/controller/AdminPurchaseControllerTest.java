package com.signasource.signa_api.admin.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.admin.dto.AdminPurchaseListItemResponse;
import com.signasource.signa_api.admin.service.AdminPurchasesService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminPurchaseControllerTest {

    @Mock private AdminPurchasesService service;

    @InjectMocks private AdminPurchaseController controller;

    @Test
    void shouldListPurchases() {
        List<AdminPurchaseListItemResponse> purchases = List.of();
        when(service.listAll()).thenReturn(purchases);

        assertSame(purchases, controller.list().getBody());
    }
}
