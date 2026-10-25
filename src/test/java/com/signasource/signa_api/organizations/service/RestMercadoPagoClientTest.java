package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
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
import com.signasource.signa_api.organizations.service.MercadoPagoClient.CheckoutPreference;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.Payment;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.PreferenceRequest;
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

    private RestMercadoPagoClient client(String accessToken, String notificationUrl) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new RestMercadoPagoClient(
                new MercadoPagoProperties(API, accessToken, null, notificationUrl), builder);
    }

    private static PreferenceRequest request(String successUrl) {
        return new PreferenceRequest(
                "purchase-1",
                "Cupos SIGNA · LSA Salud · 30 días",
                15,
                new BigDecimal("16200.00"),
                "ARS",
                successUrl,
                "https://panel/f",
                "https://panel/p");
    }

    @Test
    void shouldCreateAPreferenceWithTheItemsAndReturnUrls() {
        RestMercadoPagoClient client = client("TEST-token", "https://api.signa.ar/webhook");
        server.expect(requestTo(API + "/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TEST-token"))
                .andExpect(header("X-Idempotency-Key", Matchers.notNullValue()))
                .andExpect(jsonPath("$.external_reference").value("purchase-1"))
                .andExpect(jsonPath("$.items[0].quantity").value(15))
                .andExpect(jsonPath("$.items[0].unit_price").value(16200.00))
                .andExpect(jsonPath("$.items[0].currency_id").value("ARS"))
                .andExpect(jsonPath("$.back_urls.success").value("https://panel/s"))
                .andExpect(jsonPath("$.auto_return").value("approved"))
                .andExpect(
                        jsonPath("$.notification_url")
                                .value("https://api.signa.ar/webhook?source_news=webhooks"))
                .andRespond(
                        withSuccess(
                                "{\"id\":\"pref-1\",\"init_point\":\"https://mp/checkout\"}",
                                MediaType.APPLICATION_JSON));

        CheckoutPreference preference = client.createPreference(request("https://panel/s"));

        assertEquals(new CheckoutPreference("pref-1", "https://mp/checkout"), preference);
        server.verify();
    }

    @Test
    void shouldNotAskForAutoReturnToALocalPanel() {
        RestMercadoPagoClient client = client("TEST-token", null);
        server.expect(requestTo(API + "/checkout/preferences"))
                .andExpect(jsonPath("$.auto_return").doesNotExist())
                .andExpect(jsonPath("$.notification_url").doesNotExist())
                .andRespond(
                        withSuccess(
                                "{\"id\":\"pref-1\",\"init_point\":\"https://mp/checkout\"}",
                                MediaType.APPLICATION_JSON));

        client.createPreference(request("http://localhost:3000/s"));

        server.verify();
    }

    @Test
    void shouldFailOnAnIncompleteOrRejectedPreference() {
        RestMercadoPagoClient client = client("TEST-token", null);
        server.expect(requestTo(API + "/checkout/preferences"))
                .andRespond(withSuccess("{\"id\":\"pref-1\"}", MediaType.APPLICATION_JSON));
        assertThrows(
                PaymentProviderException.class,
                () -> client.createPreference(request("https://panel/s")));

        RestMercadoPagoClient failing = client("TEST-token", null);
        server.expect(requestTo(API + "/checkout/preferences")).andRespond(withServerError());
        assertThrows(
                PaymentProviderException.class,
                () -> failing.createPreference(request("https://panel/s")));
    }

    @Test
    void shouldReadAPayment() {
        RestMercadoPagoClient client = client("TEST-token", null);
        server.expect(requestTo(API + "/v1/payments/987"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer TEST-token"))
                .andRespond(
                        withSuccess(
                                "{\"id\":987,\"status\":\"approved\","
                                        + "\"status_detail\":\"partially_refunded\","
                                        + "\"external_reference\":\"purchase-1\","
                                        + "\"transaction_amount\":243000.0,\"currency_id\":\"ARS\"}",
                                MediaType.APPLICATION_JSON));

        Payment payment = client.getPayment("987");

        assertEquals("987", payment.id());
        assertTrue(payment.isApproved());
        assertEquals("purchase-1", payment.externalReference());
        assertEquals(0, new BigDecimal("243000").compareTo(payment.transactionAmount()));
        assertEquals("ARS", payment.currency());
        assertEquals("partially_refunded", payment.statusDetail());
        assertTrue(payment.isPartiallyRefunded());
    }

    @Test
    void shouldTreatAnUnknownPaymentAsBadInput() {
        RestMercadoPagoClient client = client("TEST-token", null);
        server.expect(requestTo(API + "/v1/payments/1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThrows(InvalidInputException.class, () -> client.getPayment("1"));
    }

    @Test
    void shouldFailOnAProviderErrorWhileReadingAPayment() {
        RestMercadoPagoClient client = client("TEST-token", null);
        server.expect(requestTo(API + "/v1/payments/1")).andRespond(withServerError());

        assertThrows(PaymentProviderException.class, () -> client.getPayment("1"));
    }

    @Test
    void shouldFailWithoutAnAccessToken() {
        RestMercadoPagoClient client = client(" ", null);

        assertThrows(PaymentProviderException.class, () -> client.getPayment("1"));
        assertThrows(
                PaymentProviderException.class,
                () -> client.createPreference(request("https://panel/s")));
    }

    @Test
    void shouldDefaultToTheProductionApi() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer defaultServer = MockRestServiceServer.bindTo(builder).build();
        RestMercadoPagoClient client =
                new RestMercadoPagoClient(new MercadoPagoProperties("", "t", null, null), builder);
        defaultServer
                .expect(requestTo("https://api.mercadopago.com/v1/payments/1"))
                .andRespond(
                        withSuccess(
                                "{\"id\":1,\"status\":\"pending\"}", MediaType.APPLICATION_JSON));

        assertEquals("pending", client.getPayment("1").status());
    }

    @Test
    void shouldRefundTheWholePaymentWithAStableIdempotencyKey() {
        RestMercadoPagoClient client = client("TEST-token", null);
        server.expect(requestTo(API + "/v1/payments/987/refunds"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer TEST-token"))
                .andExpect(header("X-Idempotency-Key", "signa-refund-987"))
                .andExpect(content().json("{}"))
                .andRespond(
                        withSuccess(
                                "{\"id\":555,\"payment_id\":987,\"status\":\"approved\"}",
                                MediaType.APPLICATION_JSON));

        assertEquals("555", client.refund("987"));
        server.verify();
    }

    @Test
    void shouldFailWhenMercadoPagoRejectsTheRefund() {
        RestMercadoPagoClient client = client("TEST-token", null);
        server.expect(requestTo(API + "/v1/payments/987/refunds"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThrows(PaymentProviderException.class, () -> client.refund("987"));
    }

    @Test
    void shouldExpireAPreference() {
        RestMercadoPagoClient client = client("TEST-token", null);
        server.expect(requestTo(API + "/checkout/preferences/pref-1"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(header("Authorization", "Bearer TEST-token"))
                .andExpect(jsonPath("$.expires").value(true))
                .andExpect(
                        jsonPath("$.expiration_date_to")
                                .value(
                                        Matchers.matchesPattern(
                                                "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}-03:00")))
                .andRespond(withSuccess("{\"id\":\"pref-1\"}", MediaType.APPLICATION_JSON));

        client.expirePreference("pref-1");
        server.verify();
    }
}
