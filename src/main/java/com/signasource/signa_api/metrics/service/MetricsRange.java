package com.signasource.signa_api.metrics.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * An instant-precise window ({@code from}–{@code to}, {@code [from, to)}) for the global usage
 * dashboard, or the whole history when both are {@code null}. Unlike the organization dashboard's
 * {@code MetricsPeriod} (calendar days in Argentina time), this keeps hour/minute precision so the
 * datetime range picker lines up with the performance (Prometheus) panels it sits next to.
 *
 * <p>Things that happened count inside {@code [start, end)}; state counts (course/lesson/enrollment
 * status) are measured as of {@link #end()}.
 */
public record MetricsRange(Instant from, Instant to) {

    /**
     * Where the dashboard's time buckets start, in seconds past a UTC bucket boundary: Argentina is
     * UTC−3 all year (no daylight saving since 2009), so its midnight is 03:00 UTC. The series
     * queries bucket by {@code FLOOR((extract(epoch from t) - BUCKET_ORIGIN_SECONDS) / bucket)}, so
     * a day bucket is an Argentine calendar day — not the UTC day, which would put everything after
     * 21:00 on the next day. Hour and minute buckets are unaffected (3 h is a whole number of
     * both).
     */
    public static final long BUCKET_ORIGIN_SECONDS = 3 * 3600L;

    public static final long DAY_SECONDS = 86400L;

    /** Start instant of the bucket a series query returned as {@code index}. */
    public static Instant bucketStart(Object index, long bucketSeconds) {
        return Instant.ofEpochSecond(
                ((Number) index).longValue() * bucketSeconds + BUCKET_ORIGIN_SECONDS);
    }

    /**
     * The Argentine calendar day a day-bucket query ({@code bucketSeconds = DAY_SECONDS}) returned
     * as {@code index}: the bucket index is the local epoch day. A {@link LocalDate} (a column that
     * already stores the Argentine day, like {@code UserDailyXp.xpDate}) passes through.
     */
    public static LocalDate localDay(Object index) {
        if (index instanceof LocalDate day) {
            return day;
        }
        return LocalDate.ofEpochDay(((Number) index).longValue());
    }

    /** Longest window a request accepts, matching the organization dashboard's cap. */
    public static final long MAX_DAYS = 366;

    private static final Instant BEGINNING = Instant.EPOCH;

    public static final MetricsRange ALL_TIME = new MetricsRange(null, null);

    /**
     * Validates request parameters: both instants or neither, in order, within {@link #MAX_DAYS}.
     */
    public static MetricsRange of(Instant from, Instant to) {
        if (from == null && to == null) {
            return ALL_TIME;
        }
        if (from == null || to == null) {
            throw new InvalidInputException("'from' and 'to' must be sent together");
        }
        if (from.isAfter(to)) {
            throw new InvalidInputException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new InvalidInputException(
                    "The period cannot be longer than " + MAX_DAYS + " days");
        }
        return new MetricsRange(from, to);
    }

    /** Builds a range from ISO-8601 instant strings (e.g. {@code 2026-11-05T13:30:00Z}). */
    public static MetricsRange fromIso(String from, String to) {
        return of(parse(from, "from"), parse(to, "to"));
    }

    private static Instant parse(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw new InvalidInputException("'" + field + "' must be an ISO-8601 instant");
        }
    }

    public boolean isOpen() {
        return from == null;
    }

    /** First instant counted. */
    public Instant start() {
        return isOpen() ? BEGINNING : from;
    }

    /** First instant after the window (exclusive); now for the whole history. */
    public Instant end() {
        return isOpen() ? Instant.now() : to;
    }

    /**
     * Resolution (in seconds) of the dashboard time-series for this window, so a short window reads
     * minute-by-minute and a long one day-by-day: minute (span ≤ 6 h), hour (≤ 3 d), else day. The
     * repositories bucket from {@link #BUCKET_ORIGIN_SECONDS} (Argentine midnight); the open
     * (all-time) range, whose span is the whole history, falls through to daily.
     */
    public long bucketSeconds() {
        long span = Duration.between(start(), end()).getSeconds();
        if (span <= 6 * 3600) {
            return 60;
        }
        if (span <= 3 * 86400) {
            return 3600;
        }
        return 86400;
    }
}
