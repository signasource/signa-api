package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.learning.entity.Sign;
import com.signasource.signa_api.learning.service.SignMediaStorage;
import java.time.Instant;
import java.util.UUID;

/**
 * Row of the admin sign catalog.
 *
 * @param lessonCount distinct lessons in draft or published course versions that reference the sign
 */
public record AdminSignResponse(
        UUID id,
        String meaning,
        String handedness,
        boolean hasAnimation,
        boolean hasYoutube,
        long openReports,
        long lessonCount,
        String animationFormat,
        Instant animationUpdatedAt) {

    public static AdminSignResponse from(Sign sign, long openReports, long lessonCount) {
        String format = SignMediaStorage.formatOf(sign.getAnimationUrl());
        return new AdminSignResponse(
                sign.getId(),
                sign.getMeaning(),
                sign.getHandedness().name(),
                format != null,
                sign.getYoutubeUrl() != null && !sign.getYoutubeUrl().isBlank(),
                openReports,
                lessonCount,
                format,
                sign.getAnimationUpdatedAt());
    }
}
