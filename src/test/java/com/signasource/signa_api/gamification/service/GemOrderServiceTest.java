package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.GemCheckoutResponse;
import com.signasource.signa_api.gamification.dto.GemOrderResponse;
import com.signasource.signa_api.gamification.entity.GemOrder;
import com.signasource.signa_api.gamification.entity.GemOrderStatus;
import com.signasource.signa_api.gamification.entity.GemPack;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.GemOrderRepository;
import com.signasource.signa_api.gamification.repository.GemPackRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.organizations.service.MercadoPagoClient;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.CheckoutOrder;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.OrderRequest;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.Payment;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GemOrderServiceTest {

    private static final String MP_ORDER = "ORD123";
    private static final String BASE_URL = "https://api.signa.test";

    @Mock private GemPackRepository packRepository;
    @Mock private GemOrderRepository orderRepository;
    @Mock private UserStatsRepository userStatsRepository;
    @Mock private MercadoPagoClient mercadoPago;

    private GemOrderService service;
    private User user;
    private UserStats stats;
    private GemPack pack;

    @BeforeEach
    void setUp() {
        user =
                User.builder()
                        .id(UUID.randomUUID())
                        .email("user@example.com")
                        .username("testuser")
                        .name("Test User")
                        .passwordHash("hashed")
                        .role(Role.USER)
                        .enabled(true)
                        .build();
        stats = UserStats.builder().id(UUID.randomUUID()).user(user).gems(100).build();
        pack =
                GemPack.builder()
                        .id(UUID.randomUUID())
                        .productId("gems_pack_300")
                        .gems(300)
                        .price(new BigDecimal("3300"))
                        .build();

        PurchaseService purchaseService =
                new PurchaseService(null, null, userStatsRepository, null);
        GemOrderSettlementService settlement =
                new GemOrderSettlementService(
                        orderRepository, userStatsRepository, purchaseService);
        service =
                new GemOrderService(
                        packRepository,
                        orderRepository,
                        settlement,
                        purchaseService,
                        mercadoPago,
                        BASE_URL);

        lenient().when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));
        lenient()
                .when(orderRepository.save(any(GemOrder.class)))
                .thenAnswer(
                        invocation -> {
                            GemOrder order = invocation.getArgument(0);
                            if (order.getId() == null) {
                                order.setId(UUID.randomUUID());
                            }
                            return order;
                        });
    }

    @Test
    void startCheckoutFreezesThePackAndReturnsTheCheckoutUrl() {
        when(packRepository.findById(pack.getId())).thenReturn(Optional.of(pack));
        when(mercadoPago.createOrder(any()))
                .thenReturn(new CheckoutOrder(MP_ORDER, "https://mp.test/checkout"));

        GemCheckoutResponse response = service.startCheckout(user, pack.getId());

        assertEquals("https://mp.test/checkout", response.checkoutUrl());
        ArgumentCaptor<OrderRequest> request = ArgumentCaptor.forClass(OrderRequest.class);
        verify(mercadoPago).createOrder(request.capture());
        assertEquals("gems_" + response.orderId(), request.getValue().externalReference());
        // Mercado Pago only accepts letters, digits, - and _ (max 64) in external_reference.
        assertTrue(request.getValue().externalReference().matches("[A-Za-z0-9_-]{1,64}"));
        assertEquals(1, request.getValue().quantity());
        assertEquals(new BigDecimal("3300"), request.getValue().unitPrice());
        assertTrue(request.getValue().successUrl().startsWith(BASE_URL));
    }

    @Test
    void startCheckoutRejectsAPackWithoutPrice() {
        pack.setPrice(BigDecimal.ZERO);
        when(packRepository.findById(pack.getId())).thenReturn(Optional.of(pack));

        assertThrows(InvalidInputException.class, () -> service.startCheckout(user, pack.getId()));
        verify(mercadoPago, never()).createOrder(any());
    }

    @Test
    void startCheckoutRejectsAnInactivePack() {
        pack.setActive(false);
        when(packRepository.findById(pack.getId())).thenReturn(Optional.of(pack));

        assertThrows(NotFoundException.class, () -> service.startCheckout(user, pack.getId()));
    }

    @Test
    void syncCreditsTheGemsOnceWhenMercadoPagoApproved() {
        GemOrder order = pendingOrder();
        stubOrder(order);
        when(mercadoPago.getOrder(MP_ORDER)).thenReturn(approved(order, "3300"));

        GemOrderResponse first = service.sync(user, order.getId());
        GemOrderResponse second = service.sync(user, order.getId());

        assertEquals(GemOrderStatus.PAID, first.status());
        assertEquals(GemOrderStatus.PAID, second.status());
        assertEquals(400, stats.getGems());
    }

    @Test
    void syncDoesNotCreditWhileThePaymentIsNotApproved() {
        GemOrder order = pendingOrder();
        stubOrder(order);
        when(mercadoPago.getOrder(MP_ORDER))
                .thenReturn(new Payment(MP_ORDER, "pending", "", order.reference(), null, "ARS"));

        GemOrderResponse response = service.sync(user, order.getId());

        assertEquals(GemOrderStatus.PENDING, response.status());
        assertEquals(100, stats.getGems());
    }

    @Test
    void syncDoesNotCreditAWrongAmount() {
        GemOrder order = pendingOrder();
        stubOrder(order);
        when(mercadoPago.getOrder(MP_ORDER)).thenReturn(approved(order, "10"));

        GemOrderResponse response = service.sync(user, order.getId());

        assertEquals(GemOrderStatus.PENDING, response.status());
        assertEquals(100, stats.getGems());
    }

    @Test
    void syncRejectsAnOrderOfAnotherUser() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdAndUserId(orderId, user.getId())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.sync(user, orderId));
    }

    @Test
    void webhookPaymentCreditsTheOrderItReferences() {
        GemOrder order = pendingOrder();
        when(orderRepository.findForUpdate(order.getId())).thenReturn(Optional.of(order));

        service.applyPayment(approved(order, "3300"));

        assertEquals(GemOrderStatus.PAID, order.getStatus());
        assertEquals(400, stats.getGems());
    }

    @Test
    void aRefundTakesTheGemsBackWithoutGoingBelowZero() {
        GemOrder order = pendingOrder();
        order.setStatus(GemOrderStatus.PAID);
        stats.setGems(50);
        when(orderRepository.findForUpdate(order.getId())).thenReturn(Optional.of(order));

        service.applyPayment(
                new Payment(
                        MP_ORDER,
                        "refunded",
                        "",
                        order.reference(),
                        new BigDecimal("3300"),
                        "ARS"));

        assertEquals(GemOrderStatus.REFUNDED, order.getStatus());
        assertEquals(0, stats.getGems());
    }

    @Test
    void aPaymentOfAnotherMercadoPagoOrderIsIgnored() {
        GemOrder order = pendingOrder();
        when(orderRepository.findForUpdate(order.getId())).thenReturn(Optional.of(order));

        service.applyPayment(
                new Payment(
                        "OTHER", "approved", "", order.reference(), new BigDecimal("3300"), "ARS"));

        assertEquals(GemOrderStatus.PENDING, order.getStatus());
        assertEquals(100, stats.getGems());
    }

    @Test
    void referencesOfSeatPurchasesAreNotGemReferences() {
        assertTrue(GemOrderService.isGemReference("gems_" + UUID.randomUUID()));
        assertFalse(GemOrderService.isGemReference(UUID.randomUUID().toString()));
        assertFalse(GemOrderService.isGemReference(null));
    }

    private GemOrder pendingOrder() {
        return GemOrder.builder()
                .id(UUID.randomUUID())
                .user(user)
                .gemPack(pack)
                .gems(300)
                .amount(new BigDecimal("3300.00"))
                .currency("ARS")
                .mercadoPagoOrderId(MP_ORDER)
                .build();
    }

    private void stubOrder(GemOrder order) {
        when(orderRepository.findByIdAndUserId(order.getId(), user.getId()))
                .thenReturn(Optional.of(order));
        lenient().when(orderRepository.findForUpdate(order.getId())).thenReturn(Optional.of(order));
    }

    private static Payment approved(GemOrder order, String amount) {
        return new Payment(
                MP_ORDER, "approved", "", order.reference(), new BigDecimal(amount), "ARS");
    }
}
