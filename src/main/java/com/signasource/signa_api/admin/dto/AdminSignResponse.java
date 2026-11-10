package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.learning.entity.Sign;
import java.util.UUID;

public record AdminSignResponse(
        UUID id,
        String meaning,
        String handedness,
        boolean hasAnimation,
        boolean hasYoutube,
        long openReports,
        long lessonCount,
        String animationFormat) {

    public static AdminSignResponse from(Sign sign, long openReports, long lessonCount) {
        String fmt = null;
        String url = sign.getAnimationUrl();
        if (url != null && !url.isBlank()) {
            fmt = url.toLowerCase().endsWith(".mp4") ? "mp4" : "glb";
        }
        return new AdminSignResponse(
                sign.getId(),
                sign.getMeaning(),
                sign.getHandedness().name(),
                url != null && !url.isBlank(),
                sign.getYoutubeUrl() != null && !sign.getYoutubeUrl().isBlank(),
                openReports,
                lessonCount,
                fmt);
    }
}
