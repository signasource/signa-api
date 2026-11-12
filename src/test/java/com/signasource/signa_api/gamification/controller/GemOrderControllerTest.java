package com.signasource.signa_api.gamification.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.gamification.dto.CreateGemOrderRequest;
import com.signasource.signa_api.gamification.dto.GemCheckoutResponse;
import com.signasource.signa_api.gamification.dto.GemOrderResponse;
import com.signasource.signa_api.gamification.dto.GemPackResponse;
import com.signasource.signa_api.gamification.dto.PendingGemOrderResponse;
import com.signasource.signa_api.gamification.repository.GemOrderRepository;
import com.signasource.signa_api.gamification.service.GemOrderService;
import com.signasource.signa_api.organizations.dto.CreateRefundRequestRequest;
import com.signasource.signa_api.organizations.dto.RefundEligibilityResponse;
import com.signasource.signa_api.organizations.dto.UserRefundRequestResponse;
import com.signasource.signa_api.organizations.entity.RefundRequestReason;
import com.signasource.signa_api.organizations.service.UserRefundRequestService;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class GemOrderControllerTest {

    @Mock private GemOrderService gemOrderService;
    @Mock private GemOrderRepository gemOrderRepository;
    @Mock private UserRefundRequestService refundRequestService;
    @Mock private CustomUserDetails userDetails;

    @InjectMocks private GemOrderController controller;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(UUID.randomUUID()).build();
        when(userDetails.getUser()).thenReturn(user);
    }

    @Test
    void getPacksReturns200() {
        List<GemPackResponse> packs = List.of();
        when(gemOrderService.getPacks()).thenReturn(packs);

        ResponseEntity<List<GemPackResponse>> response = controller.getPacks();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(packs, response.getBody());
    }

    @Test
    void createReturns201WithCheckout() {
        UUID packId = UUID.randomUUID();
        GemCheckoutResponse checkout =
                new GemCheckoutResponse(UUID.randomUUID(), "https://mp.test");
        when(gemOrderService.startCheckout(user, packId)).thenReturn(checkout);

        ResponseEntity<GemCheckoutResponse> response =
                controller.create(userDetails, new CreateGemOrderRequest(packId));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(checkout, response.getBody());
    }

    @Test
    void pendingReturns200() {
        List<PendingGemOrderResponse> pending = List.of();
        when(gemOrderService.pending(user)).thenReturn(pending);

        ResponseEntity<List<PendingGemOrderResponse>> response = controller.pending(userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(pending, response.getBody());
    }

    @Test
    void syncReturns200() {
        UUID orderId = UUID.randomUUID();
        GemOrderResponse order =
                new GemOrderResponse(
                        orderId,
                        com.signasource.signa_api.gamification.entity.GemOrderStatus.PAID,
                        100,
                        null);
        when(gemOrderService.sync(user, orderId)).thenReturn(order);

        ResponseEntity<GemOrderResponse> response = controller.sync(userDetails, orderId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(order, response.getBody());
    }

    @Test
    void historyReturns200WithEmptyList() {
        when(gemOrderRepository.findByUserIdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(List.of());

        ResponseEntity<List<com.signasource.signa_api.gamification.dto.GemOrderHistoryResponse>>
                response = controller.history(userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(), response.getBody());
    }

    @Test
    void eligibilityReturns200() {
        UUID orderId = UUID.randomUUID();
        RefundEligibilityResponse eligibility = RefundEligibilityResponse.ok();
        when(refundRequestService.checkEligibilityGems(orderId, user)).thenReturn(eligibility);

        ResponseEntity<RefundEligibilityResponse> response =
                controller.eligibility(userDetails, orderId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(eligibility, response.getBody());
    }

    @Test
    void requestRefundReturns201() {
        UUID orderId = UUID.randomUUID();
        CreateRefundRequestRequest request =
                new CreateRefundRequestRequest(RefundRequestReason.MISTAKE, "motivo");
        UserRefundRequestResponse created =
                new UserRefundRequestResponse(
                        UUID.randomUUID(),
                        "GEMS",
                        orderId,
                        "MISTAKE",
                        "motivo",
                        "PENDING",
                        null,
                        null,
                        null,
                        null);
        when(refundRequestService.createForGemOrder(orderId, user, request)).thenReturn(created);

        ResponseEntity<UserRefundRequestResponse> response =
                controller.requestRefund(userDetails, orderId, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(created, response.getBody());
    }
}
