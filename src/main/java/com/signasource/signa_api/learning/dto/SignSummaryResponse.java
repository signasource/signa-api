package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.Sign;
import java.util.UUID;

public record SignSummaryResponse(UUID id, String meaning, String handedness, String animationUrl) {
    public static SignSummaryResponse from(Sign sign) {
        return new SignSummaryResponse(
                sign.getId(),
                sign.getMeaning(),
                sign.getHandedness().name(),
                sign.getAnimationUrl());
    }
}
