package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.learning.entity.Sign;
import java.util.UUID;

public record AdminSignResponse(
        UUID id,
        String meaning,
        String handedness,
        boolean hasAnimation,
        long openReports,
        long lessonCount) {

    public static AdminSignResponse from(Sign sign, long openReports, long lessonCount) {
        return new AdminSignResponse(
                sign.getId(),
                sign.getMeaning(),
                sign.getHandedness().name(),
                sign.getAnimationUrl() != null && !sign.getAnimationUrl().isBlank(),
                openReports,
                lessonCount);
    }
}
