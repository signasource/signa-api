package com.signasource.signa_api.organizations.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.signasource.signa_api.organizations.service.MercadoPagoCheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint Mercado Pago notifies. The notification only names a payment: it is re-read from
 * the API before anything is credited. Webhooks send {@code type} and {@code data.id} as query
 * params (also in the body); the legacy IPN format sends {@code topic} and {@code id}.
 */
@RestController
@RequestMapping("/payments/mercado-pago")
@RequiredArgsConstructor
public class MercadoPagoWebhookController {

    private final MercadoPagoCheckoutService checkoutService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String topic,
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestParam(required = false) String id,
            @RequestHeader(name = "x-signature", required = false) String signature,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestBody(required = false) JsonNode body) {
        String resolvedType = type != null ? type : topic;
        String resolvedId = dataId != null ? dataId : id;
        if (body != null) {
            if (resolvedType == null && body.path("type").isTextual()) {
                resolvedType = body.path("type").asText();
            }
            if (resolvedId == null && !body.path("data").path("id").isMissingNode()) {
                resolvedId = body.path("data").path("id").asText();
            }
        }
        checkoutService.handleNotification(resolvedType, resolvedId, signature, requestId);
        return ResponseEntity.ok().build();
    }
}
