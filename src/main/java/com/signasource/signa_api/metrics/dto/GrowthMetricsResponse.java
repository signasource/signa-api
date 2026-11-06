package com.signasource.signa_api.metrics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Global growth & business snapshot for the usage dashboard's "Negocio" apartado: audience growth
 * (signups), engagement (DAU/WAU/MAU + stickiness), the waitlist funnel, B2B revenue and refunds,
 * the gem economy, seat utilization and the organization pipeline. State counts are measured as of
 * the end of the window; things that happened are counted inside it. Day-resolution series back the
 * charts (Argentina day via the stored instants).
 */
public record GrowthMetricsResponse(
        UsersSection users,
        EngagementSection engagement,
        WaitlistSection waitlist,
        RevenueSection revenue,
        GemsSection gems,
        SeatsSection seats,
        List<CountItem> organizations,
        /** Distinct users enrolled in a non-free (paid) course, as of the window's close. */
        long paidCourseUsers) {

    /** A named count, e.g. a status bucket. */
    public record CountItem(String key, long count) {}

    public record DailyCount(LocalDate day, long count) {}

    public record DailyAmount(LocalDate day, BigDecimal amount) {}

    public record UsersSection(long total, long newInRange, List<DailyCount> daily) {}

    /** Rolling active-user counts ending at the window's close, plus the daily active series. */
    public record EngagementSection(
            long dau, long wau, long mau, double stickinessPercent, List<DailyCount> dailyActive) {}

    public record WaitlistSection(long total, long newInRange, List<DailyCount> daily) {}

    public record RevenueSection(
            BigDecimal paidAmount,
            long paidCount,
            BigDecimal averageTicket,
            BigDecimal refundedAmount,
            double refundRatePercent,
            List<DailyAmount> daily) {}

    public record GemsSection(long purchases, long gemsGranted, List<DailyCount> daily) {}

    public record SeatsSection(long sold, long active, double utilizationPercent) {}
}
