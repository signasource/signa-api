package com.signasource.signa_api.learning.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.learning.dto.CourseCheckoutResponse;
import com.signasource.signa_api.learning.dto.CourseOrderHistoryResponse;
import com.signasource.signa_api.learning.dto.CourseOrderResponse;
import com.signasource.signa_api.learning.dto.CreateCourseOrderRequest;
import com.signasource.signa_api.learning.entity.CourseOrderStatus;
import com.signasource.signa_api.learning.repository.CourseOrderRepository;
import com.signasource.signa_api.learning.service.CourseOrderService;
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
class CourseOrderControllerTest {

    @Mock private CourseOrderService courseOrderService;
    @Mock private CourseOrderRepository courseOrderRepository;
    @Mock private UserRefundRequestService refundRequestService;
    @Mock private CustomUserDetails userDetails;

    @InjectMocks private CourseOrderController controller;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(UUID.randomUUID()).build();
        when(userDetails.getUser()).thenReturn(user);
    }

    @Test
    void createReturns201WithTheCheckout() {
        UUID courseId = UUID.randomUUID();
        CourseCheckoutResponse checkout =
                new CourseCheckoutResponse(UUID.randomUUID(), "https://mp.test/checkout");
        when(courseOrderService.startCheckout(user, courseId)).thenReturn(checkout);

        ResponseEntity<CourseCheckoutResponse> response =
                controller.create(userDetails, new CreateCourseOrderRequest(courseId));

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(checkout, response.getBody());
        verify(courseOrderService).startCheckout(user, courseId);
    }

    @Test
    void syncReturns200WithTheOrderState() {
        UUID orderId = UUID.randomUUID();
        CourseOrderResponse order =
                new CourseOrderResponse(orderId, CourseOrderStatus.PAID, UUID.randomUUID(), true);
        when(courseOrderService.sync(user, orderId)).thenReturn(order);

        ResponseEntity<CourseOrderResponse> response = controller.sync(userDetails, orderId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(order, response.getBody());
        verify(courseOrderService).sync(user, orderId);
    }

    @Test
    void historyReturns200WithEmptyList() {
        when(courseOrderRepository.findByUserIdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(List.of());

        ResponseEntity<List<CourseOrderHistoryResponse>> response = controller.history(userDetails);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(), response.getBody());
    }

    @Test
    void eligibilityReturns200() {
        UUID orderId = UUID.randomUUID();
        RefundEligibilityResponse eligibility = RefundEligibilityResponse.ok();
        when(refundRequestService.checkEligibilityCourse(orderId, user)).thenReturn(eligibility);

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
                        "COURSE",
                        orderId,
                        "MISTAKE",
                        "motivo",
                        "PENDING",
                        null,
                        null,
                        null,
                        null);
        when(refundRequestService.createForCourseOrder(orderId, user, request)).thenReturn(created);

        ResponseEntity<UserRefundRequestResponse> response =
                controller.requestRefund(userDetails, orderId, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(created, response.getBody());
    }
}
