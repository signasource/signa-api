package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.learning.entity.Sign;
import com.signasource.signa_api.learning.service.SignMediaStorage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * One sign for the admin sign editor.
 *
 * @param animationUrl object key in the media bucket (e.g. {@code lsa/hola.glb}), kept under its
 *     historical name; {@code mediaUrl} is the URL to actually load it from
 * @param animationFormat {@code glb}, {@code mp4} or {@code null} when there is no animation
 * @param mediaAvailable whether the file at {@code animationUrl} exists (null = could not check);
 *     imported signs point at {@code lsa/<meaning>.glb} even when nobody uploaded it
 * @param lateralAnimationUrl object key of the side-view video (.mp4 signs only)
 * @param usages every lesson block (any course version) that references the sign by meaning
 */
public record AdminSignDetailResponse(
        UUID id,
        String meaning,
        String handedness,
        String signLanguageCode,
        String animationUrl,
        boolean hasAnimation,
        String animationFormat,
        String mediaUrl,
        Boolean mediaAvailable,
        String lateralAnimationUrl,
        String lateralMediaUrl,
        Instant animationUpdatedAt,
        String youtubeUrl,
        long openReports,
        List<SignUsageResponse> usages) {

    public static AdminSignDetailResponse from(
            Sign sign,
            String mediaUrl,
            Boolean mediaAvailable,
            String lateralMediaUrl,
            long openReports,
            List<SignUsageResponse> usages) {
        return new AdminSignDetailResponse(
                sign.getId(),
                sign.getMeaning(),
                sign.getHandedness().name(),
                sign.getSignLanguage().getCode(),
                sign.getAnimationUrl(),
                SignMediaStorage.formatOf(sign.getAnimationUrl()) != null,
                SignMediaStorage.formatOf(sign.getAnimationUrl()),
                mediaUrl,
                mediaAvailable,
                sign.getLateralAnimationUrl(),
                lateralMediaUrl,
                sign.getAnimationUpdatedAt(),
                sign.getYoutubeUrl(),
                openReports,
                usages);
    }
}
