package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.common.ArgentinaTime;
import com.signasource.signa_api.exceptions.InvalidInputException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The period the organization dashboard is measured over: {@code from}–{@code to} inclusive in
 * Argentina's calendar, or the whole history when both are {@code null}.
 *
 * <p>Figures about things that happened (attempts, lessons and modules completed, minutes, active
 * days, who was active) count inside the period. Figures about a state (progress, who finished,
 * current module) are measured as of the end of the period. With no period, "active" keeps its
 * rolling windows: {@link OrganizationMetricsService#ACTIVE_WINDOW_DAYS} days for active people and
 * {@link #ROLLING_ACTIVE_DAYS} days for active days.
 */
public record MetricsPeriod(LocalDate from, LocalDate to) {

    /** Longest period a dashboard request accepts (inclusive days). */
    public static final int MAX_DAYS = 366;

    static final int ROLLING_ACTIVE_DAYS = 30;

    /** Earliest instant/date an open period reaches; avoids nullable query parameters. */
    private static final LocalDate BEGINNING = LocalDate.of(2000, 1, 1);

    public static final MetricsPeriod ALL_TIME = new MetricsPeriod(null, null);

    /** Validates request parameters: both dates or neither, in order, within {@link #MAX_DAYS}. */
    public static MetricsPeriod of(LocalDate from, LocalDate to) {
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
        return new MetricsPeriod(from, to);
    }

    public boolean isOpen() {
        return from == null;
    }

    /** First instant inside the period. */
    public Instant start() {
        return firstDay().atStartOfDay(ArgentinaTime.ZONE).toInstant();
    }

    /** First instant after the period (exclusive); now for the whole history. */
    public Instant end() {
        return isOpen()
                ? Instant.now()
                : to.plusDays(1).atStartOfDay(ArgentinaTime.ZONE).toInstant();
    }

    public LocalDate firstDay() {
        return isOpen() ? BEGINNING : from;
    }

    public LocalDate lastDay() {
        return isOpen() ? ArgentinaTime.today() : to;
    }

    /** An attempt at or after this instant (and before {@link #end()}) makes someone active. */
    public Instant activeSince() {
        return isOpen()
                ? Instant.now()
                        .minus(OrganizationMetricsService.ACTIVE_WINDOW_DAYS, ChronoUnit.DAYS)
                : start();
    }

    /** Days the "active" figure looks back over: the rolling window, or the period's length. */
    public int activeWindowDays() {
        return isOpen() ? OrganizationMetricsService.ACTIVE_WINDOW_DAYS : lengthInDays();
    }

    /** First day counted for "active days". */
    public LocalDate activeDaysSince() {
        return isOpen() ? ArgentinaTime.today().minusDays(ROLLING_ACTIVE_DAYS) : from;
    }

    private int lengthInDays() {
        return (int) ChronoUnit.DAYS.between(from, to) + 1;
    }
}
