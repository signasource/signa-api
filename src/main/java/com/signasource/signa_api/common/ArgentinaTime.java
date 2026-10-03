package com.signasource.signa_api.common;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * Calendar used for everything the user perceives as "today" (streak, daily XP, daily goal, weekly
 * XP): local time in Argentina, UTC-3 with no daylight saving.
 */
public final class ArgentinaTime {

    public static final ZoneId ZONE = ZoneId.of("America/Argentina/Cordoba");

    private ArgentinaTime() {}

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDate dayOf(Instant instant) {
        return LocalDate.ofInstant(instant, ZONE);
    }

    /** Monday 00:00 of the current week. */
    public static Instant startOfCurrentWeek() {
        return today().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .atStartOfDay(ZONE)
                .toInstant();
    }
}
