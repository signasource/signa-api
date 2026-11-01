package com.signasource.signa_api.organizations.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.signasource.signa_api.config.MercadoPagoProperties;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.PaymentProviderException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    public CheckoutOrder createOrder(OrderRequest request) {
        BigDecimal unitPrice = request.unitPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = unitPrice.multiply(BigDecimal.valueOf(request.quantity()));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("title", request.title());
        item.put("quantity", request.quantity());
        item.put("unit_price", unitPrice.toPlainString());

        Map<String, Object> online = new LinkedHashMap<>();
        online.put("success_url", request.successUrl());
        online.put("failure_url", request.failureUrl());
        online.put("pending_url", request.pendingUrl());
        // Mercado Pago only redirects on its own to an https success URL (not to localhost).
        if (request.successUrl().startsWith("https://")) {
            online.put("auto_return", "approved");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "online");
        body.put("processing_mode", "manual");
        body.put("total_amount", total.toPlainString());
        body.put("external_reference", request.externalReference());
        body.put("description", request.title());
        body.put("items", List.of(item));
        body.put("config", Map.of("online", online));

        JsonNode response =
                call(
                        () ->
                                restClient
                                        .post()
                                        .uri("/v1/orders")
                                        .headers(this::authorize)
                                        .header("X-Idempotency-Key", UUID.randomUUID().toString())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(body)
                                        .retrieve()
                                        .body(JsonNode.class));
        String id = response.path("id").asText("");
        String checkoutUrl = response.path("checkout_url").asText("");
        if (id.isEmpty() || checkoutUrl.isEmpty()) {
            throw new PaymentProviderException("Mercado Pago returned an incomplete order");
        }
        return new CheckoutOrder(id, checkoutUrl);
    }

    /** Reads an order and reports it in the payment terms the rest of the app already uses. */
    @Override
    public Payment getOrder(String orderId) {
        JsonNode response;
        try {
            response =
                    call(
                            () ->
                                    restClient
                                            .get()
                                            .uri("/v1/orders/{id}", orderId)
                                            .headers(this::authorize)
                                            .retrieve()
                                            .body(JsonNode.class));
        } catch (PaymentProviderException e) {
            if (e.getCause() instanceof RestClientResponseException http
                    && http.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new InvalidInputException("Unknown Mercado Pago order: " + orderId);
            }
            throw e;
        }
        String detail = response.path("status_detail").asText("");
        return new Payment(
                response.path("id").asText(orderId),
                paymentStatus(response.path("status").asText(""), detail),
                detail,
                response.path("external_reference").asText(""),
                amount(response.path("total_amount")),
                response.path("currency").asText(""));
    }

    @Override
    public String refund(String orderId) {
        JsonNode response =
                call(
                        () ->
                                restClient
                                        .post()
                                        .uri("/v1/orders/{id}/refund", orderId)
                                        .headers(this::authorize)
                                        // Same key for the same order: a retried refund is not
                                        // applied twice.
                                        .header("X-Idempotency-Key", "signa-refund-" + orderId)
                                        .retrieve()
                                        .body(JsonNode.class));
        JsonNode refunds = response.path("transactions").path("refunds");
        if (refunds.isArray() && !refunds.isEmpty()) {
            String refundId = refunds.get(refunds.size() - 1).path("id").asText("");
            if (!refundId.isEmpty()) {
                return refundId;
            }
        }
        return response.path("id").asText(orderId);
    }

    /** Cancels an order so it stops accepting payments (only possible before it is paid). */
    @Override
    public void cancelOrder(String orderId) {
        call(
                () ->
                        restClient
                                .post()
                                .uri("/v1/orders/{id}/cancel", orderId)
                                .headers(this::authorize)
                                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                                .retrieve()
                                .body(JsonNode.class));
    }

    /**
     * Orders report {@code processed} / {@code refunded} / {@code charged_back}; the app speaks in
     * payment statuses. A dispute still under way is {@code charged_back} with {@code in_process}.
     */
    private static String paymentStatus(String orderStatus, String detail) {
        return switch (orderStatus) {
            case "processed" -> MercadoPagoClient.STATUS_APPROVED;
            case "refunded" -> MercadoPagoClient.STATUS_REFUNDED;
            case "charged_back" ->
                    "in_process".equals(detail)
                            ? MercadoPagoClient.STATUS_IN_MEDIATION
                            : MercadoPagoClient.STATUS_CHARGED_BACK;
            default -> orderStatus;
        };
    }

    /** Orders send amounts as strings ({@code "1000.00"}); a missing one reads as zero. */
    private static BigDecimal amount(JsonNode node) {
        String text = node.asText("");
        if (text.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new PaymentProviderException("Mercado Pago returned an invalid amount: " + text);
        }
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
