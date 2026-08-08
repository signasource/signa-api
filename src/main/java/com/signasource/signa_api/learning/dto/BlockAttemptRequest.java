package com.signasource.signa_api.learning.dto;

/**
 * Optional body for a block progression. {@code isCorrect} is only meaningful for exercise blocks
 * and is required for them; it is ignored for theory/video blocks.
 */
public record BlockAttemptRequest(Boolean isCorrect) {}
