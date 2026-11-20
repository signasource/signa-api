package com.signasource.signa_api.metrics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** Which Argentine days of stored XP belong to a dashboard window. */
class XpDaysTest {

    @Test
    void xpDaysCoverAsManyDaysAsTheWindow() {
        // 24 hours ending 2026-11-20 16:30 in Argentina: only the 20th (the 19th's XP would also
        // count the hours before the window and double "XP por usuario activo").
        LocalDate[] day =
                GamificationMetricsService.xpDays(
                        Instant.parse("2026-11-19T19:30:00Z"),
                        Instant.parse("2026-11-20T19:30:00Z"));
        assertEquals(LocalDate.of(2026, 11, 20), day[0]);
        assertEquals(LocalDate.of(2026, 11, 20), day[1]);

        // 7 days → 7 calendar days (14th to 20th), not 8.
        LocalDate[] week =
                GamificationMetricsService.xpDays(
                        Instant.parse("2026-11-13T19:30:00Z"),
                        Instant.parse("2026-11-20T19:30:00Z"));
        assertEquals(LocalDate.of(2026, 11, 14), week[0]);
        assertEquals(LocalDate.of(2026, 11, 20), week[1]);

        // A window aligned to Argentine midnights keeps its first day and drops the exclusive end.
        LocalDate[] aligned =
                GamificationMetricsService.xpDays(
                        Instant.parse("2026-11-13T03:00:00Z"),
                        Instant.parse("2026-11-20T03:00:00Z"));
        assertEquals(LocalDate.of(2026, 11, 13), aligned[0]);
        assertEquals(LocalDate.of(2026, 11, 19), aligned[1]);

        // One hour with no midnight inside: that day alone.
        LocalDate[] hour =
                GamificationMetricsService.xpDays(
                        Instant.parse("2026-11-20T18:30:00Z"),
                        Instant.parse("2026-11-20T19:30:00Z"));
        assertEquals(LocalDate.of(2026, 11, 20), hour[0]);
        assertEquals(LocalDate.of(2026, 11, 20), hour[1]);
    }
}
