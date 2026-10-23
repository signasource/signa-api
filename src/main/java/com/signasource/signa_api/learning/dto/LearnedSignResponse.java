package com.signasource.signa_api.learning.dto;

/**
 * A sign the user has learned. Deliberately excludes an animation URL: {@code Sign.animationUrl} is
 * a raw R2 object key, not a usable URL — the client resolves real (presigned) animation URLs via
 * {@code POST /signs/animations}.
 */
public record LearnedSignResponse(String sign) {}
