package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.learning.entity.Sign;
import java.util.UUID;

public record AdminSignDetailResponse(
        UUID id,
        String meaning,
        String handedness,
        String signLanguageCode,
        String animationUrl,
        boolean hasAnimation,
        String youtubeUrl,
        long openReports) {

    public static AdminSignDetailResponse from(Sign sign, long openReports) {
        return new AdminSignDetailResponse(
                sign.getId(),
                sign.getMeaning(),
                sign.getHandedness().name(),
                sign.getSignLanguage().getCode(),
                sign.getAnimationUrl(),
                sign.getAnimationUrl() != null && !sign.getAnimationUrl().isBlank(),
                sign.getYoutubeUrl(),
                openReports);
    }
}
