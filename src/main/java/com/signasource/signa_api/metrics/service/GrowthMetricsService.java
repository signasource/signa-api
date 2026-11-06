package com.signasource.signa_api.metrics.service;

import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.CohortRetention;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.ConversionSection;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.CountItem;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.DailyAmount;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.DailyCount;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.EngagementSection;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.GemsSection;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.OrgUtilization;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.OrganizationsSection;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.RevenueSection;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.SeatsSection;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.SessionsSection;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.UsersSection;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.WaitlistSection;
import com.signasource.signa_api.metrics.repository.GrowthMetricsRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds the global growth snapshot by aggregating Postgres over an instant-precise window. */
@Service
@RequiredArgsConstructor
public class GrowthMetricsService {

    /** Most recent weekly cohorts kept, so an open (all-time) range stays bounded. */
    private static final int MAX_COHORTS = 12;

    /** Biggest B2B clients shown in the per-org utilization table. */
    private static final int MAX_ORGS = 20;

    private final GrowthMetricsRepository repository;

    @Transactional(readOnly = true)
    public GrowthMetricsResponse getGrowthMetrics(MetricsRange range) {
        Instant from = range.start();
        Instant until = range.end();
        // Series resolution for this window (minute/hour/day); the daily queries bucket by it.
        long bucket = range.bucketSeconds();
        // Immediately preceding, equal-length window for "+X% vs. período anterior"; absent for the
        // whole history (an open range has no comparable prior span).
        Instant previousFrom = range.isOpen() ? null : from.minus(Duration.between(from, until));
        List<CountItem> statusItems = toCountItems(repository.organizationsByStatus(until));
        long paidCourseUsers = repository.countPaidCourseUsers(until);

        return new GrowthMetricsResponse(
                users(from, until, previousFrom, bucket),
                engagement(from, until, previousFrom, bucket),
                sessions(from, until, bucket),
                cohorts(from, until),
                waitlist(from, until, bucket),
                revenue(from, until, bucket),
                new GemsSection(
                        repository.countGemPurchasesBetween(from, until),
                        repository.sumGemsGrantedBetween(from, until),
                        toDailyCounts(repository.dailyGemPurchases(from, until, bucket), bucket)),
                seats(until),
                statusItems,
                orgs(from, until, statusItems, paidCourseUsers, bucket),
                paidCourseUsers);
    }

    private UsersSection users(Instant from, Instant until, Instant previousFrom, long bucket) {
        long newInRange = repository.countNewUsersBetween(from, until);
        long activated = repository.countActivatedNewUsersBetween(from, until);
        Long previousNew =
                previousFrom == null ? null : repository.countNewUsersBetween(previousFrom, from);
        return new UsersSection(
                repository.countUsersBefore(until),
                newInRange,
                previousNew,
                activated,
                percent(activated, newInRange),
                toDailyCounts(repository.dailyNewUsers(from, until, bucket), bucket));
    }

    /** Client-reported app-usage sessions: frequency (per user) and average duration. */
    private SessionsSection sessions(Instant from, Instant until, long bucket) {
        long total = repository.countSessionsBetween(from, until);
        long users = repository.countUsersWithSessionBetween(from, until);
        double perUser = users == 0 ? 0.0 : Math.round((total * 10.0) / users) / 10.0;
        Double avg = repository.averageSessionDurationBetween(from, until);
        double averageDuration = avg == null ? 0.0 : Math.round(avg * 10.0) / 10.0;
        return new SessionsSection(
                total,
                users,
                perUser,
                averageDuration,
                toDailyCounts(repository.dailySessions(from, until, bucket), bucket));
    }

    /** Rolling windows ending at the close of the period: DAU (24 h), WAU (7 d), MAU (30 d). */
    private EngagementSection engagement(
            Instant from, Instant until, Instant previousFrom, long bucket) {
        long dau = repository.countActiveUsersBetween(until.minus(Duration.ofDays(1)), until);
        long wau = repository.countActiveUsersBetween(until.minus(Duration.ofDays(7)), until);
        long mau = repository.countActiveUsersBetween(until.minus(Duration.ofDays(30)), until);
        long activeInRange = repository.countActiveUsersBetween(from, until);
        Long previousActive =
                previousFrom == null
                        ? null
                        : repository.countActiveUsersBetween(previousFrom, from);
        // Range-based so the "usuarios activos" series follows the from/to filter.
        List<DailyCount> dailyActive =
                toDailyCounts(repository.dailyActiveUsers(from, until, bucket), bucket);
        return new EngagementSection(
                dau, wau, mau, percent(dau, mau), activeInRange, previousActive, dailyActive);
    }

    /**
     * Weekly retention cohorts over the window. Buckets each new user by the Monday of their signup
     * week, then measures the D1/D7/D30 horizons against that user's own activity days. A horizon
     * is only reported once the whole cohort week is observable at it (else {@code null} → "—"); at
     * most the {@value #MAX_COHORTS} most recent weeks are returned.
     */
    private List<CohortRetention> cohorts(Instant from, Instant until) {
        Map<UUID, LocalDate> registration = new HashMap<>();
        Map<UUID, Set<LocalDate>> activity = new HashMap<>();
        for (Object[] row : repository.cohortActivityRows(from, until)) {
            UUID userId = (UUID) row[0];
            registration.putIfAbsent(userId, (LocalDate) row[1]);
            if (row[2] != null) {
                activity.computeIfAbsent(userId, k -> new HashSet<>()).add((LocalDate) row[2]);
            }
        }

        Map<LocalDate, List<UUID>> byWeek = new TreeMap<>();
        for (Map.Entry<UUID, LocalDate> entry : registration.entrySet()) {
            LocalDate weekStart = entry.getValue().with(DayOfWeek.MONDAY);
            byWeek.computeIfAbsent(weekStart, k -> new ArrayList<>()).add(entry.getKey());
        }

        LocalDate today = LocalDate.now();
        List<CohortRetention> cohorts = new ArrayList<>(byWeek.size());
        for (Map.Entry<LocalDate, List<UUID>> entry : byWeek.entrySet()) {
            LocalDate weekStart = entry.getKey();
            List<UUID> members = entry.getValue();
            cohorts.add(
                    new CohortRetention(
                            weekStart,
                            members.size(),
                            retained(members, registration, activity, 1, weekStart, today),
                            retainedPercent(members, registration, activity, 1, weekStart, today),
                            retained(members, registration, activity, 7, weekStart, today),
                            retainedPercent(members, registration, activity, 7, weekStart, today),
                            retained(members, registration, activity, 30, weekStart, today),
                            retainedPercent(
                                    members, registration, activity, 30, weekStart, today)));
        }
        // Keep only the most recent weeks so an open range doesn't return the whole history.
        if (cohorts.size() > MAX_COHORTS) {
            cohorts = cohorts.subList(cohorts.size() - MAX_COHORTS, cohorts.size());
        }
        return cohorts;
    }

    private static Long retained(
            List<UUID> members,
            Map<UUID, LocalDate> registration,
            Map<UUID, Set<LocalDate>> activity,
            int horizon,
            LocalDate weekStart,
            LocalDate today) {
        // The whole week is observable only once its last day plus the horizon has passed.
        if (weekStart.plusDays(6).plusDays(horizon).isAfter(today)) {
            return null;
        }
        long count = 0;
        for (UUID userId : members) {
            LocalDate target = registration.get(userId).plusDays(horizon);
            if (activity.getOrDefault(userId, Set.of()).contains(target)) {
                count++;
            }
        }
        return count;
    }

    private static Double retainedPercent(
            List<UUID> members,
            Map<UUID, LocalDate> registration,
            Map<UUID, Set<LocalDate>> activity,
            int horizon,
            LocalDate weekStart,
            LocalDate today) {
        Long count = retained(members, registration, activity, horizon, weekStart, today);
        if (count == null) {
            return null;
        }
        return members.isEmpty() ? 0.0 : Math.round((count * 1000.0) / members.size()) / 10.0;
    }

    private WaitlistSection waitlist(Instant from, Instant until, long bucket) {
        return new WaitlistSection(
                repository.countWaitlistBefore(until),
                repository.countNewWaitlistBetween(from, until),
                toDailyCounts(repository.dailyWaitlist(from, until, bucket), bucket));
    }

    private RevenueSection revenue(Instant from, Instant until, long bucket) {
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
                toDailyAmounts(repository.dailyRevenue(from, until, bucket), bucket));
    }

    private SeatsSection seats(Instant until) {
        long sold = repository.countSeatsSoldBefore(until);
        long active = repository.countSeatsActiveBefore(until);
        return new SeatsSection(sold, active, percent(active, sold));
    }

    /** B2B deep-dive: pipeline, per-org license utilization and free→premium conversion. */
    private OrganizationsSection orgs(
            Instant from,
            Instant until,
            List<CountItem> statusItems,
            long paidCourseUsers,
            long bucket) {
        long pending =
                statusItems.stream()
                        .filter(i -> "PENDING".equals(i.key()))
                        .mapToLong(CountItem::count)
                        .sum();

        List<Object[]> memberRows = repository.membersByOrg(until);
        Map<UUID, Long> membersByOrg = new HashMap<>();
        long totalMembers = 0;
        for (Object[] row : memberRows) {
            long members = toLong(row[2]);
            membersByOrg.put((UUID) row[0], members);
            totalMembers += members;
        }
        double avgUsersPerOrg =
                memberRows.isEmpty()
                        ? 0.0
                        : Math.round((totalMembers * 10.0) / memberRows.size()) / 10.0;

        List<Object[]> revenueRows = repository.revenueByOrg(until);
        Map<UUID, BigDecimal> revenueByOrg = new HashMap<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (Object[] row : revenueRows) {
            BigDecimal revenue = nonNull((BigDecimal) row[1]);
            revenueByOrg.put((UUID) row[0], revenue);
            totalRevenue = totalRevenue.add(revenue);
        }
        BigDecimal avgRevenuePerOrg =
                revenueRows.isEmpty()
                        ? BigDecimal.ZERO
                        : totalRevenue.divide(
                                BigDecimal.valueOf(revenueRows.size()), 2, RoundingMode.HALF_UP);

        List<OrgUtilization> utilization = new ArrayList<>();
        for (Object[] row : repository.seatsByOrg(until)) {
            UUID orgId = (UUID) row[0];
            long sold = toLong(row[2]);
            long active = toLong(row[3]);
            utilization.add(
                    new OrgUtilization(
                            String.valueOf(orgId),
                            String.valueOf(row[1]),
                            membersByOrg.getOrDefault(orgId, 0L),
                            sold,
                            active,
                            percent(active, sold),
                            revenueByOrg.getOrDefault(orgId, BigDecimal.ZERO)));
        }
        // Biggest clients first — that's where under-utilized licenses matter most.
        utilization.sort(Comparator.comparingLong(OrgUtilization::seatsSold).reversed());
        if (utilization.size() > MAX_ORGS) {
            utilization = utilization.subList(0, MAX_ORGS);
        }

        long freeUsers = repository.countFreeCourseUsers(until);
        ConversionSection conversion =
                new ConversionSection(
                        freeUsers, paidCourseUsers, percent(paidCourseUsers, freeUsers));

        return new OrganizationsSection(
                repository.countNewOrgsBetween(from, until),
                pending,
                avgUsersPerOrg,
                avgRevenuePerOrg,
                repository.countPaidBetween(from, until),
                toDailyCounts(repository.dailyPurchases(from, until, bucket), bucket),
                utilization,
                conversion);
    }

    private static List<CountItem> toCountItems(List<Object[]> rows) {
        List<CountItem> items = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            items.add(new CountItem(String.valueOf(row[0]), toLong(row[1])));
        }
        return items;
    }

    private static List<DailyCount> toDailyCounts(List<Object[]> rows, long bucket) {
        List<DailyCount> series = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            series.add(new DailyCount(bucketStart(row[0], bucket), toLong(row[1])));
        }
        series.sort(Comparator.comparing(DailyCount::t));
        return series;
    }

    private static List<DailyAmount> toDailyAmounts(List<Object[]> rows, long bucket) {
        List<DailyAmount> series = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            series.add(new DailyAmount(bucketStart(row[0], bucket), nonNull((BigDecimal) row[1])));
        }
        series.sort(Comparator.comparing(DailyAmount::t));
        return series;
    }

    /** The UTC start instant of a bucket from its index ({@code index × bucketSeconds}). */
    private static Instant bucketStart(Object index, long bucket) {
        return Instant.ofEpochSecond(((Number) index).longValue() * bucket);
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
