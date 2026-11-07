package com.signasource.signa_api.metrics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signasource.signa_api.exceptions.NotFoundException;
import org.junit.jupiter.api.Test;

class PerfWidgetTest {

    @Test
    void resolvesKeyCaseInsensitively() {
        assertEquals(PerfWidget.LATENCY_P95, PerfWidget.fromKey("latency_p95"));
        assertEquals(PerfWidget.CPU_HOST, PerfWidget.fromKey("CPU_HOST"));
    }

    @Test
    void unknownKeyIsNotFound() {
        assertThrows(NotFoundException.class, () -> PerfWidget.fromKey("drop table"));
        assertThrows(NotFoundException.class, () -> PerfWidget.fromKey(null));
    }

    @Test
    void injectsRateWindowIntoTemplate() {
        String query = PerfWidget.LATENCY_P95.promQl("5m");
        assertTrue(query.contains("[5m]"));
        assertTrue(query.contains("histogram_quantile(0.95"));
    }

    @Test
    void windowlessWidgetIgnoresRateWindow() {
        assertEquals("signa_r2_up", PerfWidget.R2_UP.promQl("5m"));
    }

    @Test
    void inferenceRateCountsRecognitionThroughput() {
        assertEquals(PerfWidget.ML_INFERENCE_RATE, PerfWidget.fromKey("ml_inference_rate"));
        String query = PerfWidget.ML_INFERENCE_RATE.promQl("5m");
        assertTrue(query.contains("signa_client_inference_seconds_count"));
        assertTrue(query.contains("[5m]"));
    }

    @Test
    void errorRateUsesWindowTwice() {
        String query = PerfWidget.ERROR_RATE.promQl("1m");
        assertEquals(2, query.split("\\[1m]", -1).length - 1);
    }
}
