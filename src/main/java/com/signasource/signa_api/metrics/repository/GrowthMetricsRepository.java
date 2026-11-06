package com.signasource.signa_api.metrics.repository;

import com.signasource.signa_api.users.entity.User;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Read-only aggregates behind the usage dashboard's "Negocio & crecimiento" apartado: audience,
 * engagement, waitlist, B2B revenue, gems, seats and organizations, with no organization filter.
 * Grouped queries return {@code Object[]} rows mapped in {@code GrowthMetricsService}; state counts
 * are bounded by {@code until}, happenings by {@code [from, until)}. Verified against H2 in {@code
 * GrowthMetricsRepositoryTest}.
 */
public interface GrowthMetricsRepository extends Repository<User, UUID> {

    // ── Audience (signups) ───────────────────────────────────────────────────────────────────────
    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt < :until")
    long countUsersBefore(@Param("until") Instant until);

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :from AND u.createdAt < :until")
    long countNewUsersBetween(@Param("from") Instant from, @Param("until") Instant until);

    /** Rows of {@code [LocalDate day, long count]} of new accounts. */
    @Query(
            "SELECT CAST(u.createdAt AS LocalDate), COUNT(u) FROM User u "
                    + "WHERE u.createdAt >= :from AND u.createdAt < :until "
                    + "GROUP BY CAST(u.createdAt AS LocalDate)")
    List<Object[]> dailyNewUsers(@Param("from") Instant from, @Param("until") Instant until);

    // ── Engagement (active users) ────────────────────────────────────────────────────────────────
    @Query(
            "SELECT COUNT(DISTINCT a.user.id) FROM LessonBlockAttempt a "
                    + "WHERE a.attemptedAt >= :from AND a.attemptedAt < :until")
    long countActiveUsersBetween(@Param("from") Instant from, @Param("until") Instant until);

    /** Rows of {@code [LocalDate day, long distinctActiveUsers]}. */
    @Query(
            "SELECT CAST(a.attemptedAt AS LocalDate), COUNT(DISTINCT a.user.id) "
                    + "FROM LessonBlockAttempt a "
                    + "WHERE a.attemptedAt >= :from AND a.attemptedAt < :until "
                    + "GROUP BY CAST(a.attemptedAt AS LocalDate)")
    List<Object[]> dailyActiveUsers(@Param("from") Instant from, @Param("until") Instant until);

    /** New users inside the window who actually started learning (made at least one attempt). */
    @Query(
            "SELECT COUNT(DISTINCT u.id) FROM User u "
                    + "WHERE u.createdAt >= :from AND u.createdAt < :until "
                    + "AND EXISTS (SELECT 1 FROM LessonBlockAttempt a WHERE a.user = u)")
    long countActivatedNewUsersBetween(@Param("from") Instant from, @Param("until") Instant until);

    /**
     * Cohort retention raw rows: {@code [UUID userId, LocalDate registrationDay, LocalDate
     * activityDay]} for every user registered inside the window, left-joined to all their active
     * days (activity day is {@code null} for users who never came back). The service buckets these
     * into weekly cohorts and measures the D1/D7/D30 horizons in memory — portable date arithmetic
     * that neither H2 nor Postgres JPQL express cleanly.
     */
    @Query(
            "SELECT u.id, CAST(u.createdAt AS LocalDate), a.activityDate "
                    + "FROM User u LEFT JOIN UserDailyActivity a ON a.user = u "
                    + "WHERE u.createdAt >= :from AND u.createdAt < :until")
    List<Object[]> cohortActivityRows(@Param("from") Instant from, @Param("until") Instant until);

    // ── Waitlist ───────────────────────────────────────────────────────────────────────────────
    @Query("SELECT COUNT(w) FROM WaitlistEntry w WHERE w.createdAt < :until")
    long countWaitlistBefore(@Param("until") Instant until);

    @Query(
            "SELECT COUNT(w) FROM WaitlistEntry w "
                    + "WHERE w.createdAt >= :from AND w.createdAt < :until")
    long countNewWaitlistBetween(@Param("from") Instant from, @Param("until") Instant until);

    /** Rows of {@code [LocalDate day, long count]} of waitlist sign-ups. */
    @Query(
            "SELECT CAST(w.createdAt AS LocalDate), COUNT(w) FROM WaitlistEntry w "
                    + "WHERE w.createdAt >= :from AND w.createdAt < :until "
                    + "GROUP BY CAST(w.createdAt AS LocalDate)")
    List<Object[]> dailyWaitlist(@Param("from") Instant from, @Param("until") Instant until);

    // ── Revenue (B2B course purchases) ───────────────────────────────────────────────────────────
    @Query(
            "SELECT COALESCE(SUM(p.totalAmount), 0) FROM CoursePurchase p "
                    + "WHERE p.paidAt IS NOT NULL AND p.paidAt >= :from AND p.paidAt < :until")
    BigDecimal sumPaidAmountBetween(@Param("from") Instant from, @Param("until") Instant until);

    @Query(
            "SELECT COUNT(p) FROM CoursePurchase p "
                    + "WHERE p.paidAt IS NOT NULL AND p.paidAt >= :from AND p.paidAt < :until")
    long countPaidBetween(@Param("from") Instant from, @Param("until") Instant until);

    /** Rows of {@code [LocalDate day, BigDecimal amount]} of confirmed revenue. */
    @Query(
            "SELECT CAST(p.paidAt AS LocalDate), COALESCE(SUM(p.totalAmount), 0) "
                    + "FROM CoursePurchase p "
                    + "WHERE p.paidAt IS NOT NULL AND p.paidAt >= :from AND p.paidAt < :until "
                    + "GROUP BY CAST(p.paidAt AS LocalDate)")
    List<Object[]> dailyRevenue(@Param("from") Instant from, @Param("until") Instant until);

    @Query(
            "SELECT COALESCE(SUM(r.amount), 0) FROM PurchaseRefund r "
                    + "WHERE r.status = com.signasource.signa_api.organizations.entity.RefundStatus.REFUNDED "
                    + "AND r.refundedAt >= :from AND r.refundedAt < :until")
    BigDecimal sumRefundedBetween(@Param("from") Instant from, @Param("until") Instant until);

    // ── Gems (real-money gem packs) ──────────────────────────────────────────────────────────────
    @Query(
            "SELECT COUNT(g) FROM GemPurchase g "
                    + "WHERE g.verifiedAt >= :from AND g.verifiedAt < :until")
    long countGemPurchasesBetween(@Param("from") Instant from, @Param("until") Instant until);

    @Query(
            "SELECT COALESCE(SUM(g.gemsGranted), 0) FROM GemPurchase g "
                    + "WHERE g.verifiedAt >= :from AND g.verifiedAt < :until")
    long sumGemsGrantedBetween(@Param("from") Instant from, @Param("until") Instant until);

    /** Rows of {@code [LocalDate day, long count]} of real-money gem purchases. */
    @Query(
            "SELECT CAST(g.verifiedAt AS LocalDate), COUNT(g) FROM GemPurchase g "
                    + "WHERE g.verifiedAt >= :from AND g.verifiedAt < :until "
                    + "GROUP BY CAST(g.verifiedAt AS LocalDate)")
    List<Object[]> dailyGemPurchases(@Param("from") Instant from, @Param("until") Instant until);

    @Query(
            "SELECT COUNT(DISTINCT e.user.id) FROM UserCourseEnrollment e "
                    + "WHERE e.courseVersion.course.isFree = false AND e.startedAt < :until")
    long countPaidCourseUsers(@Param("until") Instant until);

    // ── Seats (B2B license utilization) ──────────────────────────────────────────────────────────
    @Query(
            "SELECT COUNT(s) FROM PurchaseSeat s "
                    + "WHERE s.purchase.paidAt IS NOT NULL AND s.purchase.paidAt < :until")
    long countSeatsSoldBefore(@Param("until") Instant until);

    @Query(
            "SELECT COUNT(s) FROM PurchaseSeat s "
                    + "WHERE s.purchase.paidAt IS NOT NULL AND s.purchase.paidAt < :until "
                    + "AND s.status = com.signasource.signa_api.organizations.entity.SeatStatus.ACTIVE")
    long countSeatsActiveBefore(@Param("until") Instant until);

    // ── Organizations ──────────────────────────────────────────────────────────────────────────
    /** Rows of {@code [OrganizationStatus status, long count]} created before {@code until}. */
    @Query(
            "SELECT o.status, COUNT(o) FROM Organization o "
                    + "WHERE o.createdAt < :until GROUP BY o.status")
    List<Object[]> organizationsByStatus(@Param("until") Instant until);

    @Query(
            "SELECT COUNT(o) FROM Organization o "
                    + "WHERE o.createdAt >= :from AND o.createdAt < :until")
    long countNewOrgsBetween(@Param("from") Instant from, @Param("until") Instant until);

    /** Rows of {@code [UUID orgId, String name, long activeMembers]} as of {@code until}. */
    @Query(
            "SELECT m.organization.id, m.organization.name, COUNT(m) FROM OrganizationMember m "
                    + "WHERE m.status = com.signasource.signa_api.organizations.entity.MemberStatus.ACTIVE "
                    + "AND m.joinedAt < :until "
                    + "GROUP BY m.organization.id, m.organization.name")
    List<Object[]> membersByOrg(@Param("until") Instant until);

    /**
     * Rows of {@code [UUID orgId, BigDecimal revenue]} of confirmed B2B revenue as of {@code
     * until}.
     */
    @Query(
            "SELECT p.organization.id, COALESCE(SUM(p.totalAmount), 0) FROM CoursePurchase p "
                    + "WHERE p.paidAt IS NOT NULL AND p.paidAt < :until "
                    + "GROUP BY p.organization.id")
    List<Object[]> revenueByOrg(@Param("until") Instant until);

    /**
     * Rows of {@code [UUID orgId, String name, long seatsSold, long seatsActive]} of paid seats.
     */
    @Query(
            "SELECT s.purchase.organization.id, s.purchase.organization.name, COUNT(s), "
                    + "SUM(CASE WHEN s.status = com.signasource.signa_api.organizations.entity.SeatStatus.ACTIVE THEN 1L ELSE 0L END) "
                    + "FROM PurchaseSeat s "
                    + "WHERE s.purchase.paidAt IS NOT NULL AND s.purchase.paidAt < :until "
                    + "GROUP BY s.purchase.organization.id, s.purchase.organization.name")
    List<Object[]> seatsByOrg(@Param("until") Instant until);

    /** Rows of {@code [LocalDate day, long count]} of confirmed purchases. */
    @Query(
            "SELECT CAST(p.paidAt AS LocalDate), COUNT(p) FROM CoursePurchase p "
                    + "WHERE p.paidAt IS NOT NULL AND p.paidAt >= :from AND p.paidAt < :until "
                    + "GROUP BY CAST(p.paidAt AS LocalDate)")
    List<Object[]> dailyPurchases(@Param("from") Instant from, @Param("until") Instant until);

    /** Distinct users enrolled in a free course, as of the window's close (the conversion base). */
    @Query(
            "SELECT COUNT(DISTINCT e.user.id) FROM UserCourseEnrollment e "
                    + "WHERE e.courseVersion.course.isFree = true AND e.startedAt < :until")
    long countFreeCourseUsers(@Param("until") Instant until);
}
