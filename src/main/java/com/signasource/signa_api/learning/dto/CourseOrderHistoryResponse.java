package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.CourseOrder;
import com.signasource.signa_api.learning.entity.CourseOrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One entry in a user's course-order purchase history. */
public record CourseOrderHistoryResponse(
        UUID id,
        UUID courseId,
        String courseName,
        BigDecimal amount,
        String currency,
        CourseOrderStatus status,
        Instant createdAt,
        Instant paidAt,
        Instant refundedAt,
        String refundRequestStatus) {

    public static CourseOrderHistoryResponse from(CourseOrder o, String refundRequestStatus) {
        return new CourseOrderHistoryResponse(
                o.getId(),
                o.getCourse().getId(),
                o.getCourse().getName(),
                o.getAmount(),
                o.getCurrency(),
                o.getStatus(),
                o.getCreatedAt(),
                o.getPaidAt(),
                o.getRefundedAt(),
                refundRequestStatus);
    }
}
