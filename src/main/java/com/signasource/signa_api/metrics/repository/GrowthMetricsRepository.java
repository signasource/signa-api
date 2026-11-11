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
    @Query(
            "SELECT COUNT(u) FROM User u WHERE u.createdAt < :until AND (u.role IS NULL OR u.role <> com.signasource.signa_api.users.entity.Role.ADMIN)")
    long countUsersBefore(@Param("until") Instant until);

    @Query(
            "SELECT COUNT(u) FROM User u WHERE u.createdAt >= :from AND u.createdAt < :until "
                    + "AND (u.role IS NULL OR u.role <> com.signasource.signa_api.users.entity.Role.ADMIN)")
    long countNewUsersBetween(@Param("from") Instant from, @Param("until") Instant until);

    /**
     * Rows of {@code [long bucketIndex, long count]} of new accounts (index × bucket = epoch s).
     */
    @Query(
            "SELECT FLOOR(extract(epoch from u.createdAt) / :bucket), COUNT(u) FROM User u "
                    + "WHERE u.createdAt >= :from AND u.createdAt < :until "
                    + "AND (u.role IS NULL OR u.role <> com.signasource.signa_api.users.entity.Role.ADMIN) "
                    + "GROUP BY 1")
    List<Object[]> dailyNewUsers(
            @Param("from") Instant from,
            @Param("until") Instant until,
            @Param("bucket") long bucket);

    // ── Engagement (active users) ────────────────────────────────────────────────────────────────
    @Query(
            "SELECT COUNT(DISTINCT a.user.id) FROM UserActivity a "
                    + "WHERE a.occurredAt >= :from AND a.occurredAt < :until")
    long countActiveUsersBetween(@Param("from") Instant from, @Param("until") Instant until);

    /** Rows of {@code [long bucketIndex, long distinctActiveUsers]}. */
    @Query(
            "SELECT FLOOR(extract(epoch from a.occurredAt) / :bucket), COUNT(DISTINCT a.user.id) "
                    + "FROM UserActivity a "
                    + "WHERE a.occurredAt >= :from AND a.occurredAt < :until "
                    + "GROUP BY 1")
    List<Object[]> dailyActiveUsers(
            @Param("from") Instant from,
            @Param("until") Instant until,
            @Param("bucket") long bucket);

    // ── Sessions (client-reported app usage)
    // ──────────────────────────────────────────────────────
    @Query(
            "SELECT COUNT(s) FROM UserSession s "
                    + "WHERE s.startedAt >= :from AND s.startedAt < :until "
                    + "AND (s.user.role IS NULL OR s.user.role <> com.signasource.signa_api.users.entity.Role.ADMIN)")
    long countSessionsBetween(@Param("from") Instant from, @Param("until") Instant until);

    @Query(
            "SELECT COUNT(DISTINCT s.user.id) FROM UserSession s "
                    + "WHERE s.startedAt >= :from AND s.startedAt < :until "
                    + "AND (s.user.role IS NULL OR s.user.role <> com.signasource.signa_api.users.entity.Role.ADMIN)")
    long countUsersWithSessionBetween(@Param("from") Instant from, @Param("until") Instant until);

    @Query(
            "SELECT AVG(s.durationSeconds) FROM UserSession s "
                    + "WHERE s.startedAt >= :from AND s.startedAt < :until "
                    + "AND (s.user.role IS NULL OR s.user.role <> com.signasource.signa_api.users.entity.Role.ADMIN)")
    Double averageSessionDurationBetween(
            @Param("from") Instant from, @Param("until") Instant until);

    /**
     * Rows of {@code [UUID userId, Instant startedAt, int durationSeconds]} of sessions that may
     * overlap the window: started in {@code [earliest, until)}. The service keeps those whose end
     * reaches {@code from} and spreads each one over the buckets it was open (a plateau, not a
     * spike).
     */
    @Query(
            "SELECT s.user.id, s.startedAt, s.durationSeconds FROM UserSession s "
                    + "WHERE s.startedAt >= :earliest AND s.startedAt < :until "
                    + "AND (s.user.role IS NULL OR s.user.role <> com.signasource.signa_api.users.entity.Role.ADMIN)")
    List<Object[]> sessionSpans(@Param("earliest") Instant earliest, @Param("until") Instant until);

    /** New users inside the window who actually started learning (made at least one attempt). */
    @Query(
            "SELECT COUNT(DISTINCT u.id) FROM User u "
                    + "WHERE u.createdAt >= :from AND u.createdAt < :until "
                    + "AND (u.role IS NULL OR u.role <> com.signasource.signa_api.users.entity.Role.ADMIN) "
                    + "AND EXISTS (SELECT 1 FROM LessonBlockAttempt a WHERE a.user = u "
                    + "AND a.attemptedAt < :until)")
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

    /** Rows of {@code [long bucketIndex, long count]} of waitlist sign-ups. */
    @Query(
            "SELECT FLOOR(extract(epoch from w.createdAt) / :bucket), COUNT(w) FROM WaitlistEntry w "
                    + "WHERE w.createdAt >= :from AND w.createdAt < :until "
                    + "GROUP BY 1")
    List<Object[]> dailyWaitlist(
            @Param("from") Instant from,
            @Param("until") Instant until,
            @Param("bucket") long bucket);

    // ── Revenue (B2B course purchases) ───────────────────────────────────────────────────────────
    @Query(
            "SELECT COALESCE(SUM(p.totalAmount), 0) FROM CoursePurchase p "
                    + "WHERE p.paidAt IS NOT NULL AND p.paidAt >= :from AND p.paidAt < :until")
    BigDecimal sumPaidAmountBetween(@Param("from") Instant from, @Param("until") Instant until);

    @Query(
            "SELECT COUNT(p) FROM CoursePurchase p "
                    + "WHERE p.paidAt IS NOT NULL AND p.paidAt >= :from AND p.paidAt < :until")
    long countPaidBetween(@Param("from") Instant from, @Param("until") Instant until);

    /** Rows of {@code [long bucketIndex, BigDecimal amount]} of confirmed revenue. */
    @Query(
            "SELECT FLOOR(extract(epoch from p.paidAt) / :bucket), COALESCE(SUM(p.totalAmount), 0) "
                    + "FROM CoursePurchase p "
                    + "WHERE p.paidAt IS NOT NULL AND p.paidAt >= :from AND p.paidAt < :until "
                    + "GROUP BY 1")
    List<Object[]> dailyRevenue(
            @Param("from") Instant from,
            @Param("until") Instant until,
            @Param("bucket") long bucket);

    @Query(
            "SELECT COALESCE(SUM(r.amount), 0) FROM PurchaseRefund r "
                    + "WHERE r.status = com.signasource.signa_api.organizations.entity.RefundStatus.REFUNDED "
                    + "AND r.refundedAt >= :from AND r.refundedAt < :until")
    BigDecimal sumRefundedBetween(@Param("from") Instant from, @Param("until") Instant until);

    // ── Gems (real-money gem packs) ──────────────────────────────────────────────────────────────
    @Query(
            "SELECT COUNT(g) FROM GemOrder g "
                    + "WHERE g.paidAt >= :from AND g.paidAt < :until "
                    + "AND g.status = com.signasource.signa_api.gamification.entity.GemOrderStatus.PAID ")
    long countGemPurchasesBetween(@Param("from") Instant from, @Param("until") Instant until);

    @Query(
            "SELECT COALESCE(SUM(g.gems), 0) FROM GemOrder g "
                    + "WHERE g.paidAt >= :from AND g.paidAt < :until "
                    + "AND g.status = com.signasource.signa_api.gamification.entity.GemOrderStatus.PAID ")
    long sumGemsGrantedBetween(@Param("from") Instant from, @Param("until") Instant until);

    /** Rows of {@code [long bucketIndex, long count]} of paid real-money gem orders. */
    @Query(
            "SELECT FLOOR(extract(epoch from g.paidAt) / :bucket), COUNT(g) FROM GemOrder g "
                    + "WHERE g.paidAt >= :from AND g.paidAt < :until "
                    + "AND g.status = com.signasource.signa_api.gamification.entity.GemOrderStatus.PAID "
                    + "GROUP BY 1")
    List<Object[]> dailyGemPurchases(
            @Param("from") Instant from,
            @Param("until") Instant until,
            @Param("bucket") long bucket);

    /**
     * Confirmed real-money revenue from gem packs (sum of the frozen order amount) in the window.
     */
    @Query(
            "SELECT COALESCE(SUM(g.amount), 0) FROM GemOrder g "
                    + "WHERE g.paidAt >= :from AND g.paidAt < :until "
                    + "AND g.status = com.signasource.signa_api.gamification.entity.GemOrderStatus.PAID ")
    BigDecimal sumGemRevenueBetween(@Param("from") Instant from, @Param("until") Instant until);

    /** Rows of {@code [long bucketIndex, BigDecimal amount]} of confirmed gem revenue. */
    @Query(
            "SELECT FLOOR(extract(epoch from g.paidAt) / :bucket), COALESCE(SUM(g.amount), 0) "
                    + "FROM GemOrder g "
                    + "WHERE g.paidAt >= :from AND g.paidAt < :until "
                    + "AND g.status = com.signasource.signa_api.gamification.entity.GemOrderStatus.PAID "
                    + "GROUP BY 1")
    List<Object[]> dailyGemRevenue(
            @Param("from") Instant from,
            @Param("until") Instant until,
            @Param("bucket") long bucket);

    @Query(
            "SELECT COALESCE(SUM(g.amount), 0) FROM GemOrder g "
                    + "WHERE g.refundedAt >= :from AND g.refundedAt < :until "
                    + "AND g.status = com.signasource.signa_api.gamification.entity.GemOrderStatus.REFUNDED ")
    BigDecimal sumGemRefundedBetween(@Param("from") Instant from, @Param("until") Instant until);

    @Query(
            "SELECT COUNT(g) FROM GemOrder g "
                    + "WHERE g.refundedAt >= :from AND g.refundedAt < :until "
                    + "AND g.status = com.signasource.signa_api.gamification.entity.GemOrderStatus.REFUNDED ")
    long countGemRefundsBetween(@Param("from") Instant from, @Param("until") Instant until);

    // ── Paid-course orders (individual, non-organization course purchases)
    // ──────────────────────────
    @Query(
            "SELECT COALESCE(SUM(c.amount), 0) FROM CourseOrder c "
                    + "WHERE c.paidAt >= :from AND c.paidAt < :until "
                    + "AND c.status = com.signasource.signa_api.learning.entity.CourseOrderStatus.PAID ")
    BigDecimal sumCourseRevenueBetween(@Param("from") Instant from, @Param("until") Instant until);

    @Query(
            "SELECT COUNT(c) FROM CourseOrder c "
                    + "WHERE c.paidAt >= :from AND c.paidAt < :until "
                    + "AND c.status = com.signasource.signa_api.learning.entity.CourseOrderStatus.PAID ")
    long countCoursePurchasesBetween(@Param("from") Instant from, @Param("until") Instant until);

    /** Rows of {@code [long bucketIndex, BigDecimal amount]} of confirmed paid-course revenue. */
    @Query(
            "SELECT FLOOR(extract(epoch from c.paidAt) / :bucket), COALESCE(SUM(c.amount), 0) "
                    + "FROM CourseOrder c "
                    + "WHERE c.paidAt >= :from AND c.paidAt < :until "
                    + "AND c.status = com.signasource.signa_api.learning.entity.CourseOrderStatus.PAID "
                    + "GROUP BY 1")
    List<Object[]> dailyCourseRevenue(
            @Param("from") Instant from,
            @Param("until") Instant until,
            @Param("bucket") long bucket);

    @Query(
            "SELECT COALESCE(SUM(c.amount), 0) FROM CourseOrder c "
                    + "WHERE c.refundedAt >= :from AND c.refundedAt < :until "
                    + "AND c.status = com.signasource.signa_api.learning.entity.CourseOrderStatus.REFUNDED ")
    BigDecimal sumCourseRefundedBetween(@Param("from") Instant from, @Param("until") Instant until);

    @Query(
            "SELECT COUNT(c) FROM CourseOrder c "
                    + "WHERE c.refundedAt >= :from AND c.refundedAt < :until "
                    + "AND c.status = com.signasource.signa_api.learning.entity.CourseOrderStatus.REFUNDED ")
    long countCourseRefundsBetween(@Param("from") Instant from, @Param("until") Instant until);

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

    /**
     * Oldest still-pending organization as of {@code until} (for the "la más antigua hace N días"
     * chip), or {@code null} when none is pending.
     */
    @Query(
            "SELECT MIN(o.createdAt) FROM Organization o "
                    + "WHERE o.createdAt < :until "
                    + "AND o.status = com.signasource.signa_api.organizations.entity.OrganizationStatus.PENDING")
    Instant oldestPendingOrgCreatedAt(@Param("until") Instant until);

    /**
     * Rows of {@code [UUID orgId, long activeMembers]}: distinct ACTIVE members of each org whose
     * user had any activity in {@code [activeFrom, until)} — the "activos 7 d" column.
     */
    @Query(
            "SELECT m.organization.id, COUNT(DISTINCT m.user.id) FROM OrganizationMember m "
                    + "WHERE m.status = com.signasource.signa_api.organizations.entity.MemberStatus.ACTIVE "
                    + "AND m.joinedAt < :until "
                    + "AND EXISTS (SELECT 1 FROM UserActivity a WHERE a.user = m.user "
                    + "AND a.occurredAt >= :activeFrom AND a.occurredAt < :until) "
                    + "GROUP BY m.organization.id")
    List<Object[]> activeMembersByOrg(
            @Param("activeFrom") Instant activeFrom, @Param("until") Instant until);

    /**
     * Rows of {@code [UUID orgId, Instant nextExpiry]}: the earliest upcoming license expiry among
     * each org's ACTIVE seats as of {@code until} — the "vence" column (a soon date is a renewal
     * risk).
     */
    @Query(
            "SELECT s.purchase.organization.id, MIN(s.accessExpiresAt) FROM PurchaseSeat s "
                    + "WHERE s.status = com.signasource.signa_api.organizations.entity.SeatStatus.ACTIVE "
                    + "AND s.accessExpiresAt IS NOT NULL AND s.accessExpiresAt >= :until "
                    + "GROUP BY s.purchase.organization.id")
    List<Object[]> nextSeatExpiryByOrg(@Param("until") Instant until);

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

    /** Rows of {@code [long bucketIndex, long count]} of confirmed purchases. */
    @Query(
            "SELECT FLOOR(extract(epoch from p.paidAt) / :bucket), COUNT(p) FROM CoursePurchase p "
                    + "WHERE p.paidAt IS NOT NULL AND p.paidAt >= :from AND p.paidAt < :until "
                    + "GROUP BY 1")
    List<Object[]> dailyPurchases(
            @Param("from") Instant from,
            @Param("until") Instant until,
            @Param("bucket") long bucket);

    /** Distinct users enrolled in a free course, as of the window's close (the conversion base). */
    @Query(
            "SELECT COUNT(DISTINCT e.user.id) FROM UserCourseEnrollment e "
                    + "WHERE e.courseVersion.course.isFree = true AND e.startedAt < :until")
    long countFreeCourseUsers(@Param("until") Instant until);
}
