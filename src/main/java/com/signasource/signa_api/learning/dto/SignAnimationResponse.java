package com.signasource.signa_api.learning.dto;

import java.util.UUID;

public record SignAnimationResponse(UUID signId, String animationUrl, long expiresInSeconds) {}
