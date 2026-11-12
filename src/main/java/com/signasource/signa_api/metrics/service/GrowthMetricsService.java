package com.signasource.signa_api.metrics.service;

import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.CohortRetention;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.ConversionSection;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.CountItem;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.CourseSalesSection;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.DailyAmount;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.DailyCount;
import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse.EndUsersSection;
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
import com.signasource.signa_api.users.service.SessionService;
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
                revenue(from, until, previousFrom, bucket),
                gems(from, until, previousFrom, bucket),
                courseSales(from, until, previousFrom, bucket),
                endUsers(from, until, previousFrom),
                seats(from, until, previousFrom),
                statusItems,
                orgs(from, until, previousFrom, statusItems, paidCourseUsers, bucket),
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
                onlineUsersSeries(
                        repository.sessionSpans(
                                from.minusSeconds(SessionService.MAX_DURATION_SECONDS), until),
                        from,
                        until,
                        bucket));
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

    private RevenueSection revenue(Instant from, Instant until, Instant previousFrom, long bucket) {
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
        // Previous equal-length window [previousFrom, from) for the period-over-period delta.
        BigDecimal previousPaid =
                previousFrom == null
                        ? null
                        : nonNull(repository.sumPaidAmountBetween(previousFrom, from));
        Long previousPaidCount =
                previousFrom == null ? null : repository.countPaidBetween(previousFrom, from);
        BigDecimal previousRefunded =
                previousFrom == null
                        ? null
                        : nonNull(repository.sumRefundedBetween(previousFrom, from));
        return new RevenueSection(
                paid,
                paidCount,
                averageTicket,
                refunded,
                refundRate,
                previousPaid,
                previousPaidCount,
                previousRefunded,
                toDailyAmounts(repository.dailyRevenue(from, until, bucket), bucket),
                combinedDailyRefunds(from, until, bucket));
    }

    /**
     * Real-money gem economy (end users, no org): money in, gems granted and refunds in the window.
     */
    private GemsSection gems(Instant from, Instant until, Instant previousFrom, long bucket) {
        BigDecimal previousPaid =
                previousFrom == null
                        ? null
                        : nonNull(repository.sumGemRevenueBetween(previousFrom, from));
        BigDecimal previousRefunded =
                previousFrom == null
                        ? null
                        : nonNull(repository.sumGemRefundedBetween(previousFrom, from));
        return new GemsSection(
                repository.countGemPurchasesBetween(from, until),
                repository.sumGemsGrantedBetween(from, until),
                nonNull(repository.sumGemRevenueBetween(from, until)),
                nonNull(repository.sumGemRefundedBetween(from, until)),
                repository.countGemRefundsBetween(from, until),
                previousPaid,
                previousRefunded,
                toDailyAmounts(repository.dailyGemRevenue(from, until, bucket), bucket));
    }

    /** Individual paid-course purchases (end users, no org): money in, count and refunds. */
    private CourseSalesSection courseSales(
            Instant from, Instant until, Instant previousFrom, long bucket) {
        BigDecimal previousPaid =
                previousFrom == null
                        ? null
                        : nonNull(repository.sumCourseRevenueBetween(previousFrom, from));
        BigDecimal previousRefunded =
                previousFrom == null
                        ? null
                        : nonNull(repository.sumCourseRefundedBetween(previousFrom, from));
        return new CourseSalesSection(
                repository.countCoursePurchasesBetween(from, until),
                nonNull(repository.sumCourseRevenueBetween(from, until)),
                nonNull(repository.sumCourseRefundedBetween(from, until)),
                repository.countCourseRefundsBetween(from, until),
                previousPaid,
                previousRefunded,
                toDailyAmounts(repository.dailyCourseRevenue(from, until, bucket), bucket));
    }

    /**
     * End users (no org) who paid in the window: distinct payers across gem and paid-course orders,
     * and the share of them with two or more paid orders (gems + courses combined). Each buyer's
     * order count is summed across both sources so a person who bought gems once and a course once
     * counts as a repeat buyer.
     */
    private EndUsersSection endUsers(Instant from, Instant until, Instant previousFrom) {
        Map<UUID, Long> ordersByUser = new HashMap<>();
        for (Object[] row : repository.gemBuyerOrderCounts(from, until)) {
            ordersByUser.merge((UUID) row[0], toLong(row[1]), Long::sum);
        }
        for (Object[] row : repository.courseBuyerOrderCounts(from, until)) {
            ordersByUser.merge((UUID) row[0], toLong(row[1]), Long::sum);
        }
        long payingUsers = ordersByUser.size();
        long repeatBuyers = ordersByUser.values().stream().filter(count -> count >= 2).count();

        Long previousPayingUsers;
        if (previousFrom == null) {
            previousPayingUsers = null;
        } else {
            Map<UUID, Long> prevOrders = new HashMap<>();
            for (Object[] row : repository.gemBuyerOrderCounts(previousFrom, from)) {
                prevOrders.merge((UUID) row[0], toLong(row[1]), Long::sum);
            }
            for (Object[] row : repository.courseBuyerOrderCounts(previousFrom, from)) {
                prevOrders.merge((UUID) row[0], toLong(row[1]), Long::sum);
            }
            previousPayingUsers = (long) prevOrders.size();
        }

        return new EndUsersSection(
                payingUsers, percent(repeatBuyers, payingUsers), previousPayingUsers);
    }

    private SeatsSection seats(Instant from, Instant until, Instant previousFrom) {
        long sold = repository.countSeatsSoldBefore(until);
        long active = repository.countSeatsActiveBefore(until);
        // State snapshot as of the window's start, for "how utilization moved over the window".
        Double previousUtilization =
                previousFrom == null
                        ? null
                        : percent(
                                repository.countSeatsActiveBefore(from),
                                repository.countSeatsSoldBefore(from));
        return new SeatsSection(sold, active, percent(active, sold), previousUtilization);
    }

    /** B2B deep-dive: pipeline, per-org license utilization and free→premium conversion. */
    private OrganizationsSection orgs(
            Instant from,
            Instant until,
            Instant previousFrom,
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
        for (Object[] row : memberRows) {
            membersByOrg.put((UUID) row[0], toLong(row[2]));
        }

        // Active members within the selected window — drives avgUsersPerOrg and the utilization
        // table's activeInRange column.
        Map<UUID, Long> activeInRangeByOrg = new HashMap<>();
        for (Object[] row : repository.activeMembersByOrg(from, until)) {
            activeInRangeByOrg.put((UUID) row[0], toLong(row[1]));
        }
        long totalActiveMembers =
                activeInRangeByOrg.values().stream().mapToLong(Long::longValue).sum();
        double avgUsersPerOrg =
                activeInRangeByOrg.isEmpty()
                        ? 0.0
                        : Math.round((totalActiveMembers * 10.0) / activeInRangeByOrg.size())
                                / 10.0;

        List<Object[]> revenueRows = repository.revenueByOrgBetween(from, until);
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

        // Next upcoming seat expiry per org.

        Map<UUID, Instant> expiryByOrg = new HashMap<>();
        for (Object[] row : repository.nextSeatExpiryByOrg(until)) {
            expiryByOrg.put((UUID) row[0], (Instant) row[1]);
        }

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
                            activeInRangeByOrg.getOrDefault(orgId, 0L),
                            expiryByOrg.get(orgId),
                            revenueByOrg.getOrDefault(orgId, BigDecimal.ZERO)));
        }
        // Riskiest first: big contracts with idle seats (sold × empty share) weigh most — that's
        // where an under-used or soon-to-renew license matters.
        utilization.sort(Comparator.comparingDouble(GrowthMetricsService::riskScore).reversed());
        if (utilization.size() > MAX_ORGS) {
            utilization = utilization.subList(0, MAX_ORGS);
        }

        long freeUsers = repository.countFreeCourseUsers(until);
        // Conversion as of the window's start, for the period-over-period delta.
        Double previousConversion =
                previousFrom == null
                        ? null
                        : percent(
                                repository.countPaidCourseUsers(from),
                                repository.countFreeCourseUsers(from));
        ConversionSection conversion =
                new ConversionSection(
                        freeUsers,
                        paidCourseUsers,
                        percent(paidCourseUsers, freeUsers),
                        previousConversion);

        return new OrganizationsSection(
                repository.countNewOrgsBetween(from, until),
                pending,
                repository.oldestPendingOrgCreatedAt(until),
                avgUsersPerOrg,
                avgRevenuePerOrg,
                repository.countPaidBetween(from, until),
                toDailyCounts(repository.dailyPurchases(from, until, bucket), bucket),
                utilization,
                conversion);
    }

    /**
     * Idle-seat exposure: contracted seats weighted by the empty share (bigger & emptier =
     * riskier).
     */
    private static double riskScore(OrgUtilization o) {
        return o.seatsSold() * (100.0 - o.occupancyPercent());
    }

    /** Combined daily refund amounts across B2B, gem-pack and paid-course sources. */
    private List<DailyAmount> combinedDailyRefunds(Instant from, Instant until, long bucket) {
        Map<Long, BigDecimal> combined = new TreeMap<>();
        for (Object[] row : repository.dailyOrgRefunds(from, until, bucket)) {
            combined.merge(toLong(row[0]), nonNull((BigDecimal) row[1]), BigDecimal::add);
        }
        for (Object[] row : repository.dailyGemRefunds(from, until, bucket)) {
            combined.merge(toLong(row[0]), nonNull((BigDecimal) row[1]), BigDecimal::add);
        }
        for (Object[] row : repository.dailyCourseRefunds(from, until, bucket)) {
            combined.merge(toLong(row[0]), nonNull((BigDecimal) row[1]), BigDecimal::add);
        }
        List<DailyAmount> result = new ArrayList<>(combined.size());
        combined.forEach(
                (b, amount) -> result.add(new DailyAmount(bucketStart(b, bucket), amount)));
        return result;
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

    /**
     * Distinct users with the app open in each bucket. A session is spread over every bucket
     * between its start and its end (start + duration), so one user connected for 15 minutes draws
     * a plateau at 1 instead of a single spike at the minute it started.
     */
    static List<DailyCount> onlineUsersSeries(
            List<Object[]> spans, Instant from, Instant until, long bucket) {
        Map<Long, Set<Object>> online = new TreeMap<>();
        long fromSec = from.getEpochSecond();
        long untilSec = until.getEpochSecond();
        for (Object[] row : spans) {
            long start = ((Instant) row[1]).getEpochSecond();
            long end = start + toLong(row[2]);
            if (end < fromSec || start >= untilSec) {
                continue;
            }
            long first = Math.max(start, fromSec) / bucket;
            long last = (Math.min(end, untilSec - 1)) / bucket;
            for (long b = first; b <= last; b++) {
                online.computeIfAbsent(b, k -> new HashSet<>()).add(row[0]);
            }
        }
        List<DailyCount> series = new ArrayList<>(online.size());
        online.forEach(
                (b, users) -> series.add(new DailyCount(bucketStart(b, bucket), users.size())));
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
