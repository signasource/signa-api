package com.signasource.signa_api.gamification.controller;

import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where Mercado Pago sends the buyer after paying a gem or course order. Returns an HTML page that
 * redirects to the app via the {@code signa://} deep link. The HTML fallback covers in-app browsers
 * that do not follow a 302 redirect to a custom URL scheme. Nothing is credited here: the app and
 * the webhook re-read the order.
 */
@RestController
@RequestMapping("/payments/mercado-pago")
public class MercadoPagoReturnController {

    private static final Set<String> STATUSES = Set.of("approved", "rejected", "pending");

    @GetMapping("/return")
    public ResponseEntity<String> back(@RequestParam(defaultValue = "pending") String status) {
        // Only a known word reaches the redirect, so nothing from the query string is echoed.
        String safe = STATUSES.contains(status) ? status : "pending";
        String deepLink = "signa://store?pago=" + safe;
        String html =
                """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <meta http-equiv="refresh" content="0;url=%s">
                  <style>
                    body{font-family:sans-serif;display:flex;flex-direction:column;
                         align-items:center;justify-content:center;min-height:100vh;
                         margin:0;background:#f5f5f5;color:#333;}
                    a{display:inline-block;margin-top:16px;padding:14px 28px;
                      background:#3d3d3d;color:#fff;border-radius:12px;
                      text-decoration:none;font-size:16px;}
                  </style>
                </head>
                <body>
                  <p>Redirigiendo a la app&hellip;</p>
                  <a href="%s">Tocar aquí si no pasa nada</a>
                  <script>window.location.href = "%s";</script>
                </body>
                </html>
                """
                        .formatted(deepLink, deepLink, deepLink);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }
}
