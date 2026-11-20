package com.signasource.signa_api.metrics.dto;

import java.util.List;

/**
 * On-device recognition metrics for the usage dashboard's "ML (reconocimiento)" apartado: total
 * camera-block predictions and per-sign recognition stats derived from PERFORM_SIGN and SPELL_NAME
 * lesson-block attempts in the window.
 */
public record MlMetricsResponse(long totalPredictions, List<SignRecognition> recognizedSigns) {

    /** One sign's camera-recognition stats (accuracy over attempts in the window). */
    public record SignRecognition(
            String sign, long attempts, long correct, double accuracyPercent) {}
}
