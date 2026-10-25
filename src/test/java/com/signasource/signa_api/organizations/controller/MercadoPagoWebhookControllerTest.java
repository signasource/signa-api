package com.signasource.signa_api.organizations.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.organizations.service.MercadoPagoCheckoutService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MercadoPagoWebhookControllerTest {

    @Mock private MercadoPagoCheckoutService checkoutService;

    @InjectMocks private MercadoPagoWebhookController controller;

    @Test
    void shouldHandleAWebhookFromItsQueryParams() {
        assertEquals(
                HttpStatus.OK,
                controller
                        .webhook("payment", null, "987", null, "sig", "req", null)
                        .getStatusCode());

        verify(checkoutService).handleNotification("payment", "987", "sig", "req");
    }

    @Test
    void shouldFallBackToTheLegacyIpnParams() {
        controller.webhook(null, "payment", null, "987", null, null, null);

        verify(checkoutService).handleNotification("payment", "987", null, null);
    }

    @Test
    void shouldReadTheTypeAndIdFromTheBody() throws Exception {
        controller.webhook(
                null,
                null,
                null,
                null,
                null,
                null,
                new ObjectMapper().readTree("{\"type\":\"payment\",\"data\":{\"id\":\"987\"}}"));

        verify(checkoutService).handleNotification("payment", "987", null, null);
    }
}
