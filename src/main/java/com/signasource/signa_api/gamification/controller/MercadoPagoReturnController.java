package com.signasource.signa_api.gamification.controller;

import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where Mercado Pago sends the buyer after paying a gem order. It only hands over to the app (deep
 * link {@code signa://}); nothing is credited here: the app and the webhook re-read the order.
 */
@RestController
@RequestMapping("/payments/mercado-pago")
public class MercadoPagoReturnController {

    private static final Set<String> STATUSES = Set.of("approved", "rejected", "pending");

    private static final String PAGE =
            """
            <!doctype html>
            <html lang="es"><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Signa</title>
            <meta http-equiv="refresh" content="0;url={{url}}">
            <style>body{font-family:system-ui,sans-serif;text-align:center;padding:48px 24px;color:#1D283C}
            a{display:inline-block;margin-top:24px;padding:14px 28px;border-radius:12px;background:#1D283C;color:#fff;text-decoration:none;font-weight:600}</style>
            </head><body>
            <h1>Volvé a Signa</h1>
            <p>Estamos confirmando tu pago. Tu compra aparece en la app en unos segundos.</p>
            <a href="{{url}}">Abrir Signa</a>
            <script>location.replace("{{url}}")</script>
            </body></html>
            """;

    @GetMapping(value = "/return", produces = MediaType.TEXT_HTML_VALUE)
    public String back(@RequestParam(defaultValue = "pending") String status) {
        // Only a known word reaches the page, so nothing from the query string is echoed.
        String safe = STATUSES.contains(status) ? status : "pending";
        return PAGE.replace("{{url}}", "signa://store?pago=" + safe);
    }
}
