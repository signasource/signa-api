package com.signasource.signa_api.metrics.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Global growth & business snapshot for the usage dashboard's "Negocio" apartado: audience growth
 * (signups), engagement (DAU/WAU/MAU + stickiness), the waitlist funnel, B2B revenue and refunds,
 * the gem economy, seat utilization and the organization pipeline. State counts are measured as of
 * the end of the window; things that happened are counted inside it. The time-series back the
 * charts at an adaptive resolution (minute/hour/day by the window span, see {@code MetricsRange}):
 * each point's {@code t} is the UTC start instant of its bucket.
 */
public record GrowthMetricsResponse(
        UsersSection users,
        EngagementSection engagement,
        SessionsSection sessions,
        List<CohortRetention> retention,
        WaitlistSection waitlist,
        RevenueSection revenue,
        GemsSection gems,
        /** Individual paid-course orders bought directly by end users (no organization). */
        CourseSalesSection courseSales,
        /** End-user (non-org) buyer counts: distinct payers and the repeat-buyer share. */
        EndUsersSection endUsers,
        SeatsSection seats,
        List<CountItem> organizations,
        /** B2B deep-dive: pipeline, per-org utilization and the free→premium conversion. */
        OrganizationsSection orgs,
        /** Distinct users enrolled in a non-free (paid) course, as of the window's close. */
        long paidCourseUsers) {

    /** A named count, e.g. a status bucket. */
    public record CountItem(String key, long count) {}

    /** One time-series point: {@code t} is the bucket's UTC start instant. */
    public record DailyCount(Instant t, long count) {}

    public record DailyAmount(Instant t, BigDecimal amount) {}

    /**
     * Signups: the audience total as of the window's close, how many are new inside it (with the
     * previous equal-length window for a period-over-period delta, {@code null} for the whole
     * history), how many of those new users actually started learning (activation), and the daily
     * series.
     */
    public record UsersSection(
            long total,
            long newInRange,
            Long previousNewInRange,
            long activatedInRange,
            double activationPercent,
            List<DailyCount> daily) {}

    /**
     * Rolling active-user counts ending at the window's close (DAU/WAU/MAU) plus stickiness, the
     * distinct active users inside the window (with the previous window for a delta, {@code null}
     * for the whole history) and the daily active series.
     */
    public record EngagementSection(
            long dau,
            long wau,
            long mau,
            double stickinessPercent,
            long activeInRange,
            Long previousActiveInRange,
            List<DailyCount> dailyActive) {}

    /**
     * App-usage sessions in the window (client-reported): the total, the distinct users they came
     * from, sessions per user (frequency) and the average session duration in seconds (time spent),
     * plus a daily series. Zero when no sessions were reported.
     */
    public record SessionsSection(
            long total,
            long users,
            double perUser,
            double averageDurationSeconds,
            List<DailyCount> daily) {}

    /**
     * Weekly retention cohort: users grouped by the ISO week they registered, and the share active
     * again exactly 1, 7 and 30 days after each member's own signup day. Counts/percentages are
     * {@code null} when the cohort's whole week is not yet observable at that horizon (so the UI
     * renders "—" instead of a misleading 0%).
     */
    public record CohortRetention(
            LocalDate weekStart,
            long cohortSize,
            Long d1,
            Double d1Percent,
            Long d7,
            Double d7Percent,
            Long d30,
            Double d30Percent) {}

    public record WaitlistSection(long total, long newInRange, List<DailyCount> daily) {}

    /**
     * B2B revenue in the window, with the immediately preceding equal-length window for a
     * period-over-period delta ({@code previous*} are {@code null} for the whole history).
     */
    public record RevenueSection(
            BigDecimal paidAmount,
            long paidCount,
            BigDecimal averageTicket,
            BigDecimal refundedAmount,
            double refundRatePercent,
            BigDecimal previousPaidAmount,
            Long previousPaidCount,
            BigDecimal previousRefundedAmount,
            List<DailyAmount> daily,
            List<DailyAmount> dailyRefunded) {}

    /**
     * Real-money gem-pack economy (end users, no organization): paid orders and gems granted, the
     * money taken in and given back (refunds/chargebacks) in the window, and the revenue
     * time-series.
     */
    public record GemsSection(
            long purchases,
            long gemsGranted,
            BigDecimal paidAmount,
            BigDecimal refundedAmount,
            long refundedCount,
            BigDecimal previousPaidAmount,
            BigDecimal previousRefundedAmount,
            List<DailyAmount> daily) {}

    /**
     * Individual paid-course purchases (end users, no organization): how many courses were bought
     * and for how much, the money refunded in the window, and the revenue time-series.
     */
    public record CourseSalesSection(
            long purchases,
            BigDecimal paidAmount,
            BigDecimal refundedAmount,
            long refundedCount,
            BigDecimal previousPaidAmount,
            BigDecimal previousRefundedAmount,
            List<DailyAmount> daily) {}

    /**
     * End users (no organization) who paid for anything (gem packs or individual paid courses) in
     * the window: how many distinct people paid, and what share of them bought two or more times
     * (gem and course orders combined). Drives "conversión a pago", "usuarios que compraron",
     * "ingreso por usuario pagador" and "recompra" in the Negocio tab's "Usuarios finales" panel.
     */
    public record EndUsersSection(
            long payingUsers, double repeatBuyerPercent, Long previousPayingUsers) {}

    /**
     * Seat utilization as of the window's close, with the utilization as of the window's start for
     * a delta ({@code previousUtilizationPercent} is {@code null} for the whole history).
     */
    public record SeatsSection(
            long sold, long active, double utilizationPercent, Double previousUtilizationPercent) {}

    /**
     * B2B organizations deep-dive: new orgs and those pending approval, average members and revenue
     * per org, purchases in the window (with a daily series), the per-org license utilization table
     * (contracted vs. used seats — a 100-seat org using 5 is a commercial signal) and the free→
     * premium conversion.
     */
    public record OrganizationsSection(
            long newInRange,
            long pending,
            /** When the oldest still-pending org was created ({@code null} if none pending). */
            Instant oldestPendingCreatedAt,
            double avgUsersPerOrg,
            BigDecimal avgRevenuePerOrg,
            long purchasesInRange,
            List<DailyCount> dailyPurchases,
            List<OrgUtilization> utilization,
            ConversionSection conversion) {}

    /**
     * Per-org license utilization, ranked by renewal/under-use risk. {@code activeInRange} is the
     * members active within the selected window; {@code expiresAt} is the next upcoming seat expiry
     * ({@code null} when the org has no active seat with a future expiry).
     */
    public record OrgUtilization(
            String orgId,
            String name,
            long members,
            long seatsSold,
            long seatsActive,
            double occupancyPercent,
            long activeInRange,
            Instant expiresAt,
            BigDecimal revenue) {}

    /**
     * Free→premium conversion as of the window's close, with the conversion as of the window's
     * start for a delta ({@code previousConversionPercent} is {@code null} for the whole history).
     */
    public record ConversionSection(
            long freeCourseUsers,
            long paidCourseUsers,
            double conversionPercent,
            Double previousConversionPercent) {}
}
