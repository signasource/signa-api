package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.signasource.signa_api.config.MercadoPagoProperties;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.PaymentProviderException;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.CheckoutOrder;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.OrderRequest;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.Payment;
import java.math.BigDecimal;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestMercadoPagoClientTest {

    private static final String API = "https://mp.test";

    private MockRestServiceServer server;

    private RestMercadoPagoClient client(String accessToken) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new RestMercadoPagoClient(
                new MercadoPagoProperties(API, accessToken, null), builder);
    }

    private static OrderRequest request(String successUrl) {
        return new OrderRequest(
                "purchase-1",
                "Cupos SIGNA · LSA Salud · 30 días",
                15,
                new BigDecimal("16200.00"),
                "ARS",
                successUrl,
                "https://panel/f",
                "https://panel/p");
    }

    private static String order(String status, String detail) {
        return "{\"id\":\"ORD1\",\"status\":\""
                + status
                + "\",\"status_detail\":\""
                + detail
                + "\",\"external_reference\":\"purchase-1\","
                + "\"total_amount\":\"243000.00\",\"currency\":\"ARS\"}";
    }

    @Test
    void shouldCreateAnOrderWithTheItemsAndReturnUrls() {
        RestMercadoPagoClient client = client("TEST-token");
        server.expect(requestTo(API + "/v1/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TEST-token"))
                .andExpect(header("X-Idempotency-Key", Matchers.notNullValue()))
                .andExpect(jsonPath("$.type").value("online"))
                .andExpect(jsonPath("$.processing_mode").value("manual"))
                .andExpect(jsonPath("$.total_amount").value("243000.00"))
                .andExpect(jsonPath("$.external_reference").value("purchase-1"))
                .andExpect(jsonPath("$.items[0].quantity").value(15))
                .andExpect(jsonPath("$.items[0].unit_price").value("16200.00"))
                .andExpect(jsonPath("$.config.online.success_url").value("https://panel/s"))
                .andExpect(jsonPath("$.config.online.failure_url").value("https://panel/f"))
                .andExpect(jsonPath("$.config.online.pending_url").value("https://panel/p"))
                .andExpect(jsonPath("$.config.online.auto_return").value("approved"))
                .andRespond(
                        withSuccess(
                                "{\"id\":\"ORD1\",\"checkout_url\":\"https://mp/checkout\"}",
                                MediaType.APPLICATION_JSON));

        CheckoutOrder preference = client.createOrder(request("https://panel/s"));

        assertEquals(new CheckoutOrder("ORD1", "https://mp/checkout"), preference);
        server.verify();
    }

    @Test
    void shouldNotAskForAutoReturnToALocalPanel() {
        RestMercadoPagoClient client = client("TEST-token");
        server.expect(requestTo(API + "/v1/orders"))
                .andExpect(jsonPath("$.config.online.auto_return").doesNotExist())
                .andRespond(
                        withSuccess(
                                "{\"id\":\"ORD1\",\"checkout_url\":\"https://mp/checkout\"}",
                                MediaType.APPLICATION_JSON));

        client.createOrder(request("http://localhost:3000/s"));

        server.verify();
    }

    @Test
    void shouldFailOnAnIncompleteOrRejectedOrder() {
        RestMercadoPagoClient client = client("TEST-token");
        server.expect(requestTo(API + "/v1/orders"))
                .andRespond(withSuccess("{\"id\":\"ORD1\"}", MediaType.APPLICATION_JSON));
        assertThrows(
                PaymentProviderException.class,
                () -> client.createOrder(request("https://panel/s")));

        RestMercadoPagoClient failing = client("TEST-token");
        server.expect(requestTo(API + "/v1/orders")).andRespond(withServerError());
        assertThrows(
                PaymentProviderException.class,
                () -> failing.createOrder(request("https://panel/s")));
    }

    @Test
    void shouldReadAProcessedOrderAsAnApprovedPayment() {
        RestMercadoPagoClient client = client("TEST-token");
        server.expect(requestTo(API + "/v1/orders/ORD1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer TEST-token"))
                .andRespond(
                        withSuccess(order("processed", "accredited"), MediaType.APPLICATION_JSON));

        Payment payment = client.getOrder("ORD1");

        assertEquals("ORD1", payment.id());
        assertTrue(payment.isApproved());
        assertEquals("purchase-1", payment.externalReference());
        assertEquals(0, new BigDecimal("243000").compareTo(payment.transactionAmount()));
        assertEquals("ARS", payment.currency());
        assertEquals("accredited", payment.statusDetail());
    }

    @Test
    void shouldReadAPartialRefundAsAnApprovedPaymentPartlyGivenBack() {
        RestMercadoPagoClient client = client("TEST-token");
        server.expect(requestTo(API + "/v1/orders/ORD1"))
                .andRespond(
                        withSuccess(
                                order("processed", "partially_refunded"),
                                MediaType.APPLICATION_JSON));

        assertTrue(client.getOrder("ORD1").isPartiallyRefunded());
    }

    @Test
    void shouldReadRefundsAndDisputesInPaymentTerms() {
        RestMercadoPagoClient refunded = client("TEST-token");
        server.expect(requestTo(API + "/v1/orders/ORD1"))
                .andRespond(withSuccess(order("refunded", "refunded"), MediaType.APPLICATION_JSON));
        assertTrue(refunded.getOrder("ORD1").isRefunded());

        RestMercadoPagoClient dispute = client("TEST-token");
        server.expect(requestTo(API + "/v1/orders/ORD1"))
                .andRespond(
                        withSuccess(
                                order("charged_back", "in_process"), MediaType.APPLICATION_JSON));
        assertTrue(dispute.getOrder("ORD1").isInMediation());

        RestMercadoPagoClient chargeback = client("TEST-token");
        server.expect(requestTo(API + "/v1/orders/ORD1"))
                .andRespond(
                        withSuccess(order("charged_back", "settled"), MediaType.APPLICATION_JSON));
        assertTrue(chargeback.getOrder("ORD1").isChargedBack());
    }

    @Test
    void shouldKeepAnUnpaidOrderStatusAsIs() {
        RestMercadoPagoClient client = client("TEST-token");
        server.expect(requestTo(API + "/v1/orders/ORD1"))
                .andRespond(withSuccess(order("created", "created"), MediaType.APPLICATION_JSON));

        Payment payment = client.getOrder("ORD1");

        assertEquals("created", payment.status());
        assertEquals(false, payment.isApproved());
    }

    @Test
    void shouldTreatAnUnknownOrderAsBadInput() {
        RestMercadoPagoClient client = client("TEST-token");
        server.expect(requestTo(API + "/v1/orders/ORD1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(InvalidInputException.class, () -> client.getOrder("ORD1"));
    }

    @Test
    void shouldFailOnAProviderErrorOrABadAmountWhileReadingAnOrder() {
        RestMercadoPagoClient failing = client("TEST-token");
        server.expect(requestTo(API + "/v1/orders/ORD1")).andRespond(withServerError());
        assertThrows(PaymentProviderException.class, () -> failing.getOrder("ORD1"));

        RestMercadoPagoClient garbled = client("TEST-token");
        server.expect(requestTo(API + "/v1/orders/ORD1"))
                .andRespond(
                        withSuccess(
                                "{\"id\":\"ORD1\",\"status\":\"processed\",\"total_amount\":\"abc\"}",
                                MediaType.APPLICATION_JSON));
        assertThrows(PaymentProviderException.class, () -> garbled.getOrder("ORD1"));
    }

    @Test
    void shouldFailWithoutAnAccessToken() {
        RestMercadoPagoClient client = client(" ");

        assertThrows(PaymentProviderException.class, () -> client.getOrder("ORD1"));
        assertThrows(
                PaymentProviderException.class,
                () -> client.createOrder(request("https://panel/s")));
    }

    @Test
    void shouldDefaultToTheProductionApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer defaultServer = MockRestServiceServer.bindTo(builder).build();
        RestMercadoPagoClient client =
                new RestMercadoPagoClient(new MercadoPagoProperties("", "t", null), builder);
        defaultServer
                .expect(requestTo("https://api.mercadopago.com/v1/orders/ORD1"))
                .andRespond(
                        withSuccess(
                                "{\"id\":\"ORD1\",\"status\":\"action_required\"}",
                                MediaType.APPLICATION_JSON));

        assertEquals("action_required", client.getOrder("ORD1").status());
    }

    @Test
    void shouldRefundTheWholeOrderWithAStableIdempotencyKey() {
        RestMercadoPagoClient client = client("TEST-token");
        server.expect(requestTo(API + "/v1/orders/ORD1/refund"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TEST-token"))
                .andExpect(header("X-Idempotency-Key", "signa-refund-ORD1"))
                .andRespond(
                        withSuccess(
                                "{\"id\":\"ORD1\",\"status\":\"refunded\","
                                        + "\"transactions\":{\"refunds\":[{\"id\":\"REF1\"}]}}",
                                MediaType.APPLICATION_JSON));

        assertEquals("REF1", client.refund("ORD1"));
        server.verify();
    }

    @Test
    void shouldFallBackToTheOrderIdWhenTheRefundHasNoId() {
        RestMercadoPagoClient client = client("TEST-token");
        server.expect(requestTo(API + "/v1/orders/ORD1/refund"))
                .andRespond(
                        withSuccess(
                                "{\"id\":\"ORD1\",\"status\":\"refunded\"}",
                                MediaType.APPLICATION_JSON));

        assertEquals("ORD1", client.refund("ORD1"));
    }

    @Test
    void shouldFailWhenMercadoPagoRejectsTheRefund() {
        RestMercadoPagoClient client = client("TEST-token");
        server.expect(requestTo(API + "/v1/orders/ORD1/refund"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThrows(PaymentProviderException.class, () -> client.refund("ORD1"));
    }

    @Test
    void shouldCancelAnOrder() {
        RestMercadoPagoClient client = client("TEST-token");
        server.expect(requestTo(API + "/v1/orders/ORD1/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TEST-token"))
                .andExpect(header("X-Idempotency-Key", Matchers.notNullValue()))
                .andRespond(
                        withSuccess(
                                "{\"id\":\"ORD1\",\"status\":\"canceled\"}",
                                MediaType.APPLICATION_JSON));

        client.cancelOrder("ORD1");
        server.verify();
    }
}
