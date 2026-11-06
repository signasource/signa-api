package com.signasource.signa_api.metrics.service;

import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.CountItem;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.DailyAmount;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.DailyCount;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.EngagementSection;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.GemsSection;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.RevenueSection;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.SeatsSection;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.UsersSection;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.WaitlistSection;
import com.signasource.signa_api.metrics.repository.GrowthMetricsRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the global growth snapshot by aggregating Postgres over an instant-precise window. */
@Service
@RequiredArgsConstructor
public class GrowthMetricsService {

    private final GrowthMetricsRepository repository;

    @Transactional(readOnly = true)
    public GrowthMetricsResponse getGrowthMetrics(MetricsRange range) {
        Instant from = range.start();
        Instant until = range.end();

        return new GrowthMetricsResponse(
                users(from, until),
                engagement(from, until),
                waitlist(from, until),
                revenue(from, until),
                new GemsSection(
                        repository.countGemPurchasesBetween(from, until),
                        repository.sumGemsGrantedBetween(from, until),
                        toDailyCounts(repository.dailyGemPurchases(from, until))),
                seats(until),
                toCountItems(repository.organizationsByStatus(until)),
                repository.countPaidCourseUsers(until));
    }

    private UsersSection users(Instant from, Instant until) {
        return new UsersSection(
                repository.countUsersBefore(until),
                repository.countNewUsersBetween(from, until),
                toDailyCounts(repository.dailyNewUsers(from, until)));
    }

    /** Rolling windows ending at the close of the period: DAU (24 h), WAU (7 d), MAU (30 d). */
    private EngagementSection engagement(Instant from, Instant until) {
        long dau = repository.countActiveUsersBetween(until.minus(Duration.ofDays(1)), until);
        long wau = repository.countActiveUsersBetween(until.minus(Duration.ofDays(7)), until);
        long mau = repository.countActiveUsersBetween(until.minus(Duration.ofDays(30)), until);
        // Range-based so the "usuarios activos por día" series follows the from/to filter.
        List<DailyCount> dailyActive = toDailyCounts(repository.dailyActiveUsers(from, until));
        return new EngagementSection(dau, wau, mau, percent(dau, mau), dailyActive);
    }

    private WaitlistSection waitlist(Instant from, Instant until) {
        return new WaitlistSection(
                repository.countWaitlistBefore(until),
                repository.countNewWaitlistBetween(from, until),
                toDailyCounts(repository.dailyWaitlist(from, until)));
    }

    private RevenueSection revenue(Instant from, Instant until) {
        BigDecimal paid = nonNull(repository.sumPaidAmountBetween(from, until));
        long paidCount = repository.countPaidBetween(from, until);
        BigDecimal refunded = nonNull(repository.sumRefundedBetween(from, until));
        BigDecimal averageTicket =
                paidCount == 0
                        ? BigDecimal.ZERO
                        : paid.divide(BigDecimal.valueOf(paidCount), 2, RoundingMode.HALF_UP);
        double refundRate =
                paid.signum() == 0
                        ? 0.0
                        : refunded.multiply(BigDecimal.valueOf(100))
                                .divide(paid, 1, RoundingMode.HALF_UP)
                                .doubleValue();
        return new RevenueSection(
                paid,
                paidCount,
                averageTicket,
                refunded,
                refundRate,
                toDailyAmounts(repository.dailyRevenue(from, until)));
    }

    private SeatsSection seats(Instant until) {
        long sold = repository.countSeatsSoldBefore(until);
        long active = repository.countSeatsActiveBefore(until);
        return new SeatsSection(sold, active, percent(active, sold));
    }

    private static List<CountItem> toCountItems(List<Object[]> rows) {
        List<CountItem> items = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            items.add(new CountItem(String.valueOf(row[0]), toLong(row[1])));
        }
        return items;
    }

    private static List<DailyCount> toDailyCounts(List<Object[]> rows) {
        List<DailyCount> series = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            series.add(new DailyCount((LocalDate) row[0], toLong(row[1])));
        }
        series.sort(Comparator.comparing(DailyCount::day));
        return series;
    }

    private static List<DailyAmount> toDailyAmounts(List<Object[]> rows) {
        List<DailyAmount> series = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            series.add(new DailyAmount((LocalDate) row[0], nonNull((BigDecimal) row[1])));
        }
        series.sort(Comparator.comparing(DailyAmount::day));
        return series;
    }

    private static long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** A part-of-whole percentage rounded to one decimal (0 when the whole is 0). */
    private static double percent(long part, long whole) {
        return whole == 0 ? 0.0 : Math.round((part * 1000.0) / whole) / 10.0;
    }
}
