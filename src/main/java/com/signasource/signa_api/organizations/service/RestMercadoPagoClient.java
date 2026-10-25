package com.signasource.signa_api.organizations.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.signasource.signa_api.config.MercadoPagoProperties;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.PaymentProviderException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Mercado Pago over its REST API. Bodies are built as maps and read as trees on purpose: the app's
 * {@code ObjectMapper} is configured for our own DTOs, and Mercado Pago's field names must not
 * depend on it.
 */
@Component
public class RestMercadoPagoClient implements MercadoPagoClient {

    private static final String DEFAULT_API_URL = "https://api.mercadopago.com";
    private static final DateTimeFormatter MERCADO_PAGO_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx");
    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    private final MercadoPagoProperties properties;
    private final RestClient restClient;

    public RestMercadoPagoClient(MercadoPagoProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        String apiUrl =
                properties.apiUrl() == null || properties.apiUrl().isBlank()
                        ? DEFAULT_API_URL
                        : properties.apiUrl();
        this.restClient = builder.baseUrl(apiUrl).build();
    }

    @Override
    public CheckoutPreference createPreference(PreferenceRequest request) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", request.externalReference());
        item.put("title", request.title());
        item.put("quantity", request.quantity());
        item.put("unit_price", request.unitPrice());
        item.put("currency_id", request.currency());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item));
        body.put("external_reference", request.externalReference());
        body.put("statement_descriptor", "SIGNA");
        body.put(
                "back_urls",
                Map.of(
                        "success", request.successUrl(),
                        "failure", request.failureUrl(),
                        "pending", request.pendingUrl()));
        // Mercado Pago only redirects on its own to an https success URL (not to localhost).
        if (request.successUrl().startsWith("https://")) {
            body.put("auto_return", "approved");
        }
        if (properties.notificationUrl() != null && !properties.notificationUrl().isBlank()) {
            body.put("notification_url", properties.notificationUrl() + "?source_news=webhooks");
        }

        JsonNode response =
                call(
                        () ->
                                restClient
                                        .post()
                                        .uri("/checkout/preferences")
                                        .headers(this::authorize)
                                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(body)
                                        .retrieve()
                                        .body(JsonNode.class));
        String id = response.path("id").asText("");
        String checkoutUrl = response.path("init_point").asText("");
        if (id.isEmpty() || checkoutUrl.isEmpty()) {
            throw new PaymentProviderException("Mercado Pago returned an incomplete preference");
        }
        return new CheckoutPreference(id, checkoutUrl);
    }

    @Override
    public Payment getPayment(String paymentId) {
        JsonNode response;
        try {
            response =
                    call(
                            () ->
                                    restClient
                                            .get()
                                            .uri("/v1/payments/{id}", paymentId)
                                            .headers(this::authorize)
                                            .retrieve()
                                            .body(JsonNode.class));
        } catch (PaymentProviderException e) {
            if (e.getCause() instanceof RestClientResponseException http
                    && http.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new InvalidInputException("Unknown Mercado Pago payment: " + paymentId);
            }
            throw e;
        }
        return new Payment(
                response.path("id").asText(paymentId),
                response.path("status").asText(""),
                response.path("status_detail").asText(""),
                response.path("external_reference").asText(""),
                response.path("transaction_amount").decimalValue(),
                response.path("currency_id").asText(""));
    }

    @Override
    public String refund(String paymentId) {
        JsonNode response =
                call(
                        () ->
                                restClient
                                        .post()
                                        .uri("/v1/payments/{id}/refunds", paymentId)
                                        .headers(this::authorize)
                                        // Same key for the same payment: a retried refund is not
                                        // applied twice.
                                        .header("X-Idempotency-Key", "signa-refund-" + paymentId)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(Map.of())
                                        .retrieve()
                                        .body(JsonNode.class));
        return response.path("id").asText("");
    }

    @Override
    public void expirePreference(String preferenceId) {
        call(
                () ->
                        restClient
                                .put()
                                .uri("/checkout/preferences/{id}", preferenceId)
                                .headers(this::authorize)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        Map.of(
                                                "expires",
                                                true,
                                                "expiration_date_to",
                                                OffsetDateTime.now(ARGENTINA)
                                                        .format(MERCADO_PAGO_DATE)))
                                .retrieve()
                                .body(JsonNode.class));
    }

    private void authorize(HttpHeaders headers) {
        headers.setBearerAuth(properties.accessToken());
    }

    private JsonNode call(Supplier<JsonNode> request) {
        if (!properties.isConfigured()) {
            throw new PaymentProviderException("Mercado Pago is not configured");
        }
        try {
            JsonNode response = request.get();
            if (response == null) {
                throw new PaymentProviderException("Mercado Pago returned an empty response");
            }
            return response;
        } catch (RestClientException e) {
            throw new PaymentProviderException("Mercado Pago request failed", e);
        }
    }
}
