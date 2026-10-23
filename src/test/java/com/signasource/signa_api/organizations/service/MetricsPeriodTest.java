package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signasource.signa_api.common.ArgentinaTime;
import com.signasource.signa_api.exceptions.InvalidInputException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class MetricsPeriodTest {

    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    @Test
    void shouldMeanTheWholeHistoryWithoutDates() {
        MetricsPeriod period = MetricsPeriod.of(null, null);

        assertSame(MetricsPeriod.ALL_TIME, period);
        assertTrue(period.isOpen());
        assertEquals(LocalDate.of(2000, 1, 1), period.firstDay());
        assertEquals(ArgentinaTime.today(), period.lastDay());
        assertEquals(OrganizationMetricsService.ACTIVE_WINDOW_DAYS, period.activeWindowDays());
        assertEquals(
                ArgentinaTime.today().minusDays(MetricsPeriod.ROLLING_ACTIVE_DAYS),
                period.activeDaysSince());
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        assertTrue(
                Math.abs(ChronoUnit.SECONDS.between(weekAgo, period.activeSince())) < 5,
                "active window is the last week");
        assertFalse(period.end().isAfter(Instant.now()));
    }

    @Test
    void shouldCoverWholeArgentineDays() {
        MetricsPeriod period = MetricsPeriod.of(FROM, TO);

        assertFalse(period.isOpen());
        assertEquals(FROM.atStartOfDay(ArgentinaTime.ZONE).toInstant(), period.start());
        assertEquals(TO.plusDays(1).atStartOfDay(ArgentinaTime.ZONE).toInstant(), period.end());
        assertEquals(FROM, period.firstDay());
        assertEquals(TO, period.lastDay());
        assertEquals(period.start(), period.activeSince());
        assertEquals(30, period.activeWindowDays());
        assertEquals(FROM, period.activeDaysSince());
    }

    @Test
    void shouldAcceptUpToTheLongestPeriod() {
        MetricsPeriod period = MetricsPeriod.of(FROM, FROM.plusDays(MetricsPeriod.MAX_DAYS - 1L));

        assertEquals(MetricsPeriod.MAX_DAYS, period.activeWindowDays());
    }

    @Test
    void shouldRejectInvalidPeriods() {
        assertThrows(InvalidInputException.class, () -> MetricsPeriod.of(FROM, null));
        assertThrows(InvalidInputException.class, () -> MetricsPeriod.of(null, TO));
        assertThrows(InvalidInputException.class, () -> MetricsPeriod.of(TO, FROM));
        assertThrows(
                InvalidInputException.class,
                () -> MetricsPeriod.of(FROM, FROM.plusDays(MetricsPeriod.MAX_DAYS)));
    }
}
