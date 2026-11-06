package com.signasource.signa_api.metrics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signasource.signa_api.exceptions.InvalidInputException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MetricsRangeTest {

    private static final Instant FROM = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-11-02T00:00:00Z");

    @Test
    void allTimeWhenBothNull() {
        MetricsRange range = MetricsRange.of(null, null);
        assertTrue(range.isOpen());
        assertEquals(Instant.EPOCH, range.start());
    }

    @Test
    void closedRangeKeepsBounds() {
        MetricsRange range = MetricsRange.of(FROM, TO);
        assertFalse(range.isOpen());
        assertEquals(FROM, range.start());
        assertEquals(TO, range.end());
    }

    @Test
    void rejectsLoneBound() {
        assertThrows(InvalidInputException.class, () -> MetricsRange.of(FROM, null));
        assertThrows(InvalidInputException.class, () -> MetricsRange.of(null, TO));
    }

    @Test
    void rejectsReversedRange() {
        assertThrows(InvalidInputException.class, () -> MetricsRange.of(TO, FROM));
    }

    @Test
    void rejectsTooLongRange() {
        assertThrows(
                InvalidInputException.class,
                () -> MetricsRange.of(FROM, FROM.plus(400, java.time.temporal.ChronoUnit.DAYS)));
    }

    @Test
    void parsesIsoStrings() {
        MetricsRange range = MetricsRange.fromIso("2026-11-01T00:00:00Z", "2026-11-02T00:00:00Z");
        assertEquals(FROM, range.start());
        assertEquals(TO, range.end());
    }

    @Test
    void blankIsoBecomesAllTime() {
        assertSame(MetricsRange.ALL_TIME, MetricsRange.fromIso(null, ""));
    }

    @Test
    void rejectsMalformedIso() {
        assertThrows(InvalidInputException.class, () -> MetricsRange.fromIso("not-a-date", null));
    }

    @Test
    void openEndIsNow() {
        Instant before = Instant.now();
        Instant end = MetricsRange.ALL_TIME.end();
        assertTrue(!end.isBefore(before));
    }

    @Test
    void bucketSecondsScalesWithSpan() {
        assertEquals(
                60L,
                MetricsRange.of(FROM, FROM.plus(30, java.time.temporal.ChronoUnit.MINUTES))
                        .bucketSeconds());
        assertEquals(
                3600L,
                MetricsRange.of(FROM, FROM.plus(2, java.time.temporal.ChronoUnit.DAYS))
                        .bucketSeconds());
        assertEquals(
                86400L,
                MetricsRange.of(FROM, FROM.plus(30, java.time.temporal.ChronoUnit.DAYS))
                        .bucketSeconds());
        assertEquals(86400L, MetricsRange.ALL_TIME.bucketSeconds());
    }
}
