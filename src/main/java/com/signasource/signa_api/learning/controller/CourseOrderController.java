package com.signasource.signa_api.learning.controller;

import com.signasource.signa_api.auth.entity.CustomUserDetails;
import com.signasource.signa_api.learning.dto.CourseCheckoutResponse;
import com.signasource.signa_api.learning.dto.CourseOrderResponse;
import com.signasource.signa_api.learning.dto.CreateCourseOrderRequest;
import com.signasource.signa_api.learning.service.CourseOrderService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CourseOrderController {

    private final CourseOrderService courseOrderService;

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
}
