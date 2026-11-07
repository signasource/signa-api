package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.CourseOrder;
import com.signasource.signa_api.learning.entity.CourseOrderStatus;
import java.util.UUID;

/** State of a course order after a sync. {@code owned} is {@code true} once access was granted. */
public record CourseOrderResponse(UUID id, CourseOrderStatus status, UUID courseId, boolean owned) {

    public static CourseOrderResponse from(CourseOrder order) {
        return new CourseOrderResponse(
                order.getId(),
                order.getStatus(),
                order.getCourse().getId(),
                order.getStatus() == CourseOrderStatus.PAID);
    }
}
