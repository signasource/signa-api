package com.signasource.signa_api.learning.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.learning.dto.CourseCheckoutResponse;
import com.signasource.signa_api.learning.dto.CourseOrderHistoryResponse;
import com.signasource.signa_api.learning.dto.CourseOrderResponse;
import com.signasource.signa_api.learning.dto.CreateCourseOrderRequest;
import com.signasource.signa_api.learning.repository.CourseOrderRepository;
import com.signasource.signa_api.learning.service.CourseOrderService;
import com.signasource.signa_api.organizations.dto.CreateRefundRequestRequest;
import com.signasource.signa_api.organizations.dto.RefundEligibilityResponse;
import com.signasource.signa_api.organizations.dto.UserRefundRequestResponse;
import com.signasource.signa_api.organizations.service.UserRefundRequestService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CourseOrderController {

    private final CourseOrderService courseOrderService;
    private final CourseOrderRepository courseOrderRepository;
    private final UserRefundRequestService refundRequestService;

    @PostMapping("/store/course-orders")
    public ResponseEntity<CourseCheckoutResponse> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateCourseOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courseOrderService.startCheckout(userDetails.getUser(), request.courseId()));
    }

    @PostMapping("/store/course-orders/{orderId}/sync")
    public ResponseEntity<CourseOrderResponse> sync(
            @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable UUID orderId) {
        return ResponseEntity.ok(courseOrderService.sync(userDetails.getUser(), orderId));
    }

    @GetMapping("/store/course-orders")
    public ResponseEntity<List<CourseOrderHistoryResponse>> history(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<CourseOrderHistoryResponse> result =
                courseOrderRepository
                        .findByUserIdOrderByCreatedAtDesc(userDetails.getUser().getId())
                        .stream()
                        .map(CourseOrderHistoryResponse::from)
                        .toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/store/course-orders/{orderId}/refund-eligibility")
    public ResponseEntity<RefundEligibilityResponse> eligibility(
            @AuthenticationPrincipal CustomUserDetails userDetails, @PathVariable UUID orderId) {
        return ResponseEntity.ok(
                refundRequestService.checkEligibilityCourse(orderId, userDetails.getUser()));
    }

    @PostMapping("/store/course-orders/{orderId}/refund-request")
    public ResponseEntity<UserRefundRequestResponse> requestRefund(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID orderId,
            @Valid @RequestBody CreateRefundRequestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(refundRequestService.createForCourseOrder(
                        orderId, userDetails.getUser(), request));
    }
}
