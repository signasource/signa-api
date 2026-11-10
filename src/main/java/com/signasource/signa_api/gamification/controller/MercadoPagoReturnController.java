package com.signasource.signa_api.gamification.controller;

import java.net.URI;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where Mercado Pago sends the buyer after paying a gem or course order. It shows nothing: it
 * redirects straight to the app (deep link {@code signa://}). Nothing is credited here: the app and
 * the webhook re-read the order.
 */
@RestController
@RequestMapping("/payments/mercado-pago")
public class MercadoPagoReturnController {

    private static final Set<String> STATUSES = Set.of("approved", "rejected", "pending");

    @GetMapping("/return")
    public ResponseEntity<Void> back(@RequestParam(defaultValue = "pending") String status) {
        // Only a known word reaches the redirect, so nothing from the query string is echoed.
        String safe = STATUSES.contains(status) ? status : "pending";
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, URI.create("signa://store?pago=" + safe).toString())
                .build();
    }
}
