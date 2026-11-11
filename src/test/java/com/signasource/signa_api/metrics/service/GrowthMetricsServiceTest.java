package com.signasource.signa_api.metrics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.metrics.dto.GrowthMetricsResponse;
import com.signasource.signa_api.metrics.repository.GrowthMetricsRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrowthMetricsServiceTest {

    @Mock private GrowthMetricsRepository repository;
    @InjectMocks private GrowthMetricsService service;

    private final MetricsRange range =
            MetricsRange.of(
                    Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-08T00:00:00Z"));

    @BeforeEach
    void stubs() {
        when(repository.countUsersBefore(any())).thenReturn(500L);
        when(repository.countNewUsersBetween(any(), any())).thenReturn(40L);
        when(repository.countActivatedNewUsersBetween(any(), any())).thenReturn(24L);
        // Daily series now return [bucketIndex, value]; for a day bucket the index is the epoch
        // day.
        when(repository.dailyNewUsers(any(), any(), anyLong()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {LocalDate.of(2026, 11, 2).toEpochDay(), 10L}));
        // Three rolling windows, called in order: DAU, WAU, MAU.
        when(repository.countActiveUsersBetween(any(), any())).thenReturn(3L, 10L, 25L);
        when(repository.dailyActiveUsers(any(), any(), anyLong()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {LocalDate.of(2026, 11, 2).toEpochDay(), 8L}));
        when(repository.countWaitlistBefore(any())).thenReturn(800L);
        when(repository.countNewWaitlistBetween(any(), any())).thenReturn(50L);
        when(repository.dailyWaitlist(any(), any(), anyLong()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {LocalDate.of(2026, 11, 3).toEpochDay(), 5L}));
        when(repository.sumPaidAmountBetween(any(), any())).thenReturn(new BigDecimal("10000.00"));
        when(repository.countPaidBetween(any(), any())).thenReturn(4L);
        when(repository.sumRefundedBetween(any(), any())).thenReturn(new BigDecimal("1000.00"));
        when(repository.dailyRevenue(any(), any(), anyLong()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {
                                    LocalDate.of(2026, 11, 4).toEpochDay(),
                                    new BigDecimal("6000.00")
                                }));
        when(repository.countGemPurchasesBetween(any(), any())).thenReturn(7L);
        when(repository.sumGemsGrantedBetween(any(), any())).thenReturn(3500L);
        when(repository.sumGemRevenueBetween(any(), any())).thenReturn(new BigDecimal("20000.00"));
        when(repository.sumGemRefundedBetween(any(), any())).thenReturn(new BigDecimal("500.00"));
        when(repository.countGemRefundsBetween(any(), any())).thenReturn(1L);
        when(repository.dailyGemRevenue(any(), any(), anyLong()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {
                                    LocalDate.of(2026, 11, 2).toEpochDay(),
                                    new BigDecimal("8000.00")
                                }));
        when(repository.sumCourseRevenueBetween(any(), any()))
                .thenReturn(new BigDecimal("50000.00"));
        when(repository.countCoursePurchasesBetween(any(), any())).thenReturn(3L);
        when(repository.sumCourseRefundedBetween(any(), any())).thenReturn(BigDecimal.ZERO);
        when(repository.countCourseRefundsBetween(any(), any())).thenReturn(0L);
        when(repository.dailyCourseRevenue(any(), any(), anyLong()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {
                                    LocalDate.of(2026, 11, 5).toEpochDay(),
                                    new BigDecimal("50000.00")
                                }));
        when(repository.countPaidCourseUsers(any())).thenReturn(33L);
        when(repository.countSeatsSoldBefore(any())).thenReturn(10L);
        when(repository.countSeatsActiveBefore(any())).thenReturn(6L);
        when(repository.organizationsByStatus(any()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {"ACTIVE", 3L}, new Object[] {"PENDING", 2L}));
    }

    @Test
    void assemblesAudienceAndWaitlist() {
        GrowthMetricsResponse r = service.getGrowthMetrics(range);

        assertEquals(500L, r.users().total());
        assertEquals(40L, r.users().newInRange());
        assertEquals(1, r.users().daily().size());
        // The epoch-day bucket index maps back to that day's UTC-midnight instant.
        assertEquals(
                LocalDate.of(2026, 11, 2).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                r.users().daily().get(0).t());
        assertEquals(800L, r.waitlist().total());
        assertEquals(50L, r.waitlist().newInRange());
    }

    @Test
    void computesSessionsPerUserAndAverageDuration() {
        when(repository.countSessionsBetween(any(), any())).thenReturn(30L);
        when(repository.countUsersWithSessionBetween(any(), any())).thenReturn(12L);
        when(repository.averageSessionDurationBetween(any(), any())).thenReturn(184.4);
        when(repository.sessionSpans(any(), any()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {
                                    java.util.UUID.randomUUID(),
                                    java.time.Instant.parse("2026-11-02T10:00:00Z"),
                                    60
                                }));

        var sessions = service.getGrowthMetrics(range).sessions();

        assertEquals(30L, sessions.total());
        assertEquals(12L, sessions.users());
        assertEquals(2.5, sessions.perUser()); // 30 / 12
        assertEquals(184.4, sessions.averageDurationSeconds());
        assertEquals(1, sessions.daily().size());
    }

    @Test
    void handlesNoSessionsWithoutNullPointer() {
        var sessions = service.getGrowthMetrics(MetricsRange.ALL_TIME).sessions();

        assertEquals(0L, sessions.total());
        assertEquals(0.0, sessions.perUser());
        assertEquals(0.0, sessions.averageDurationSeconds());
    }

    @Test
    void reportsActivationAndPeriodOverPeriodComparison() {
        GrowthMetricsResponse r = service.getGrowthMetrics(range);

        assertEquals(24L, r.users().activatedInRange());
        assertEquals(60.0, r.users().activationPercent());
        // Closed range → the previous equal-length window is comparable (same stubbed counts).
        assertEquals(40L, r.users().previousNewInRange());
        assertEquals(25L, r.engagement().activeInRange());
        assertEquals(25L, r.engagement().previousActiveInRange());
        // Negocio KPIs now carry a previous equal-length window too (same stubbed counts).
        assertEquals(0, new BigDecimal("10000.00").compareTo(r.revenue().previousPaidAmount()));
        assertEquals(4L, r.revenue().previousPaidCount());
        assertEquals(0, new BigDecimal("1000.00").compareTo(r.revenue().previousRefundedAmount()));
        assertEquals(60.0, r.seats().previousUtilizationPercent());
        assertEquals(0.0, r.orgs().conversion().previousConversionPercent());
    }

    @Test
    void bucketsWeeklyRetentionCohorts() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        LocalDate reg = LocalDate.of(2026, 1, 5); // a Monday, long enough ago to be observable
        when(repository.cohortActivityRows(any(), any()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {a, reg, reg.plusDays(1)}, // D1 hit
                                new Object[] {a, reg, reg.plusDays(7)}, // D7 hit
                                new Object[] {b, reg, null})); // never returned

        GrowthMetricsResponse r = service.getGrowthMetrics(range);

        assertEquals(1, r.retention().size());
        var cohort = r.retention().get(0);
        assertEquals(reg, cohort.weekStart());
        assertEquals(2L, cohort.cohortSize());
        assertEquals(1L, cohort.d1());
        assertEquals(50.0, cohort.d1Percent());
        assertEquals(1L, cohort.d7());
        assertEquals(0L, cohort.d30());
        assertEquals(0.0, cohort.d30Percent());
    }

    @Test
    void leavesUnobservableCohortHorizonsNull() {
        UUID a = UUID.randomUUID();
        LocalDate reg =
                LocalDate.now().with(java.time.DayOfWeek.MONDAY); // current week, D7/D30 open
        when(repository.cohortActivityRows(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {a, reg, null}));

        var cohort = service.getGrowthMetrics(range).retention().get(0);

        assertEquals(null, cohort.d7());
        assertEquals(null, cohort.d7Percent());
        assertEquals(null, cohort.d30());
    }

    @Test
    void buildsOrganizationsDeepDiveAndConversion() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(repository.countNewOrgsBetween(any(), any())).thenReturn(4L);
        when(repository.membersByOrg(any()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {a, "Org A", 10L}, new Object[] {b, "Org B", 5L}));
        when(repository.revenueByOrg(any()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {a, new BigDecimal("100000")},
                                new Object[] {b, new BigDecimal("50000")}));
        when(repository.seatsByOrg(any()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {a, "Org A", 100L, 5L}, // 5% occupancy, 100 seats
                                new Object[] {b, "Org B", 20L, 18L})); // 90% occupancy
        when(repository.dailyPurchases(any(), any(), anyLong()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {LocalDate.of(2026, 11, 4).toEpochDay(), 2L}));
        when(repository.countFreeCourseUsers(any())).thenReturn(200L); // paid 33 → 16.5%
        Instant expiryA = Instant.parse("2026-12-01T00:00:00Z");
        when(repository.oldestPendingOrgCreatedAt(any()))
                .thenReturn(Instant.parse("2026-11-02T00:00:00Z"));
        when(repository.activeMembersByOrg(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {a, 7L}));
        when(repository.nextSeatExpiryByOrg(any()))
                .thenReturn(List.<Object[]>of(new Object[] {a, expiryA}));

        var orgs = service.getGrowthMetrics(range).orgs();

        assertEquals(4L, orgs.newInRange());
        assertEquals(2L, orgs.pending());
        assertEquals(Instant.parse("2026-11-02T00:00:00Z"), orgs.oldestPendingCreatedAt());
        assertEquals(7.5, orgs.avgUsersPerOrg()); // (10+5)/2
        assertEquals(0, new BigDecimal("75000.00").compareTo(orgs.avgRevenuePerOrg()));
        assertEquals(4L, orgs.purchasesInRange());
        assertEquals(2, orgs.utilization().size());
        // Biggest client first; its licenses are badly under-used (5/100).
        assertEquals("Org A", orgs.utilization().get(0).name());
        assertEquals(100L, orgs.utilization().get(0).seatsSold());
        assertEquals(5.0, orgs.utilization().get(0).occupancyPercent());
        assertEquals(10L, orgs.utilization().get(0).members());
        assertEquals(7L, orgs.utilization().get(0).active7d());
        assertEquals(expiryA, orgs.utilization().get(0).expiresAt());
        // Org B has no active-7d / expiry rows → defaults.
        assertEquals(0L, orgs.utilization().get(1).active7d());
        assertEquals(null, orgs.utilization().get(1).expiresAt());
        assertEquals(200L, orgs.conversion().freeCourseUsers());
        assertEquals(33L, orgs.conversion().paidCourseUsers());
        assertEquals(16.5, orgs.conversion().conversionPercent());
    }

    @Test
    void omitsComparisonForOpenRange() {
        GrowthMetricsResponse r = service.getGrowthMetrics(MetricsRange.ALL_TIME);

        assertEquals(null, r.users().previousNewInRange());
        assertEquals(null, r.engagement().previousActiveInRange());
        assertEquals(null, r.revenue().previousPaidAmount());
        assertEquals(null, r.revenue().previousPaidCount());
        assertEquals(null, r.seats().previousUtilizationPercent());
        assertEquals(null, r.orgs().conversion().previousConversionPercent());
        assertEquals(0, r.retention().size());
    }

    @Test
    void computesEngagementWindowsAndStickiness() {
        GrowthMetricsResponse r = service.getGrowthMetrics(range);

        assertEquals(3L, r.engagement().dau());
        assertEquals(10L, r.engagement().wau());
        assertEquals(25L, r.engagement().mau());
        assertEquals(12.0, r.engagement().stickinessPercent());
        assertEquals(1, r.engagement().dailyActive().size());
    }

    @Test
    void computesRevenueTicketAndRefundRate() {
        GrowthMetricsResponse r = service.getGrowthMetrics(range);

        assertEquals(0, new BigDecimal("10000.00").compareTo(r.revenue().paidAmount()));
        assertEquals(4L, r.revenue().paidCount());
        assertEquals(0, new BigDecimal("2500.00").compareTo(r.revenue().averageTicket()));
        assertEquals(10.0, r.revenue().refundRatePercent());
        assertEquals(1, r.revenue().daily().size());
    }

    @Test
    void computesSeatUtilizationAndGems() {
        GrowthMetricsResponse r = service.getGrowthMetrics(range);

        assertEquals(10L, r.seats().sold());
        assertEquals(6L, r.seats().active());
        assertEquals(60.0, r.seats().utilizationPercent());
        assertEquals(7L, r.gems().purchases());
        assertEquals(3500L, r.gems().gemsGranted());
        assertEquals(1, r.gems().daily().size());
        assertEquals(33L, r.paidCourseUsers());
        assertEquals("ACTIVE", r.organizations().get(0).key());
    }

    @Test
    void computesEndUserGemAndCourseSales() {
        GrowthMetricsResponse r = service.getGrowthMetrics(range);

        assertEquals(0, new BigDecimal("20000.00").compareTo(r.gems().paidAmount()));
        assertEquals(0, new BigDecimal("500.00").compareTo(r.gems().refundedAmount()));
        assertEquals(1L, r.gems().refundedCount());

        assertEquals(3L, r.courseSales().purchases());
        assertEquals(0, new BigDecimal("50000.00").compareTo(r.courseSales().paidAmount()));
        assertEquals(0, BigDecimal.ZERO.compareTo(r.courseSales().refundedAmount()));
        assertEquals(0L, r.courseSales().refundedCount());
        assertEquals(1, r.courseSales().daily().size());
    }

    @Test
    void countsDistinctPayersAndRepeatBuyersAcrossGemsAndCourses() {
        UUID a = UUID.randomUUID(); // 1 gem + 1 course order → repeat buyer
        UUID b = UUID.randomUUID(); // 2 gem orders → repeat buyer
        UUID c = UUID.randomUUID(); // 1 course order only → single buyer
        when(repository.gemBuyerOrderCounts(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {a, 1L}, new Object[] {b, 2L}));
        when(repository.courseBuyerOrderCounts(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {a, 1L}, new Object[] {c, 1L}));

        var endUsers = service.getGrowthMetrics(range).endUsers();

        assertEquals(3L, endUsers.payingUsers()); // a, b, c distinct
        assertEquals(66.7, endUsers.repeatBuyerPercent()); // a and b of 3 → 2/3
    }

    @Test
    void handlesNoPayersWithoutDivisionByZero() {
        var endUsers = service.getGrowthMetrics(range).endUsers();

        assertEquals(0L, endUsers.payingUsers());
        assertEquals(0.0, endUsers.repeatBuyerPercent());
    }

    @Test
    void handlesEmptyRevenueWithoutDivisionByZero() {
        when(repository.sumPaidAmountBetween(any(), any())).thenReturn(BigDecimal.ZERO);
        when(repository.countPaidBetween(any(), any())).thenReturn(0L);

        GrowthMetricsResponse r = service.getGrowthMetrics(range);

        assertEquals(0, BigDecimal.ZERO.compareTo(r.revenue().averageTicket()));
        assertEquals(0.0, r.revenue().refundRatePercent());
    }
}
