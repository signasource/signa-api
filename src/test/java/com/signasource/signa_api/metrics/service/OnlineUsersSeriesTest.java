package com.signasource.signa_api.metrics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OnlineUsersSeriesTest {

    private static final Instant FROM = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant UNTIL = Instant.parse("2026-11-10T11:00:00Z");

    @Test
    void oneUserConnectedFor15MinutesDrawsAPlateauAtOne() {
        UUID user = UUID.randomUUID();
        var series =
                GrowthMetricsService.onlineUsersSeries(
                        List.<Object[]>of(new Object[] {user, FROM, 15 * 60}), FROM, UNTIL, 60);

        assertEquals(16, series.size()); // 10:00 … 10:15
        assertTrue(series.stream().allMatch(p -> p.count() == 1));
    }

    @Test
    void overlappingSessionsOfTheSameUserCountOnce() {
        UUID user = UUID.randomUUID();
        var series =
                GrowthMetricsService.onlineUsersSeries(
                        List.<Object[]>of(
                                new Object[] {user, FROM, 600},
                                new Object[] {user, FROM.plusSeconds(60), 600}),
                        FROM,
                        UNTIL,
                        60);

        assertTrue(series.stream().allMatch(p -> p.count() == 1));
    }

    @Test
    void aSessionStartedBeforeTheWindowStillShowsInside() {
        var series =
                GrowthMetricsService.onlineUsersSeries(
                        List.<Object[]>of(
                                new Object[] {UUID.randomUUID(), FROM.minusSeconds(120), 600}),
                        FROM,
                        UNTIL,
                        60);

        assertEquals(FROM, series.get(0).t());
        assertEquals(1, series.get(0).count());
    }
}
