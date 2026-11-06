package com.signasource.signa_api.metrics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
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
        when(repository.dailyNewUsers(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {LocalDate.of(2026, 11, 2), 10L}));
        // Three rolling windows, called in order: DAU, WAU, MAU.
        when(repository.countActiveUsersBetween(any(), any())).thenReturn(3L, 10L, 25L);
        when(repository.dailyActiveUsers(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {LocalDate.of(2026, 11, 2), 8L}));
        when(repository.countWaitlistBefore(any())).thenReturn(800L);
        when(repository.countNewWaitlistBetween(any(), any())).thenReturn(50L);
        when(repository.dailyWaitlist(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {LocalDate.of(2026, 11, 3), 5L}));
        when(repository.sumPaidAmountBetween(any(), any())).thenReturn(new BigDecimal("10000.00"));
        when(repository.countPaidBetween(any(), any())).thenReturn(4L);
        when(repository.sumRefundedBetween(any(), any())).thenReturn(new BigDecimal("1000.00"));
        when(repository.dailyRevenue(any(), any()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {
                                    LocalDate.of(2026, 11, 4), new BigDecimal("6000.00")
                                }));
        when(repository.countGemPurchasesBetween(any(), any())).thenReturn(7L);
        when(repository.sumGemsGrantedBetween(any(), any())).thenReturn(3500L);
        when(repository.dailyGemPurchases(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {LocalDate.of(2026, 11, 2), 2L}));
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
        assertEquals(800L, r.waitlist().total());
        assertEquals(50L, r.waitlist().newInRange());
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
        when(repository.dailyPurchases(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {LocalDate.of(2026, 11, 4), 2L}));
        when(repository.countFreeCourseUsers(any())).thenReturn(200L); // paid 33 → 16.5%

        var orgs = service.getGrowthMetrics(range).orgs();

        assertEquals(4L, orgs.newInRange());
        assertEquals(2L, orgs.pending());
        assertEquals(7.5, orgs.avgUsersPerOrg()); // (10+5)/2
        assertEquals(0, new BigDecimal("75000.00").compareTo(orgs.avgRevenuePerOrg()));
        assertEquals(4L, orgs.purchasesInRange());
        assertEquals(2, orgs.utilization().size());
        // Biggest client first; its licenses are badly under-used (5/100).
        assertEquals("Org A", orgs.utilization().get(0).name());
        assertEquals(100L, orgs.utilization().get(0).seatsSold());
        assertEquals(5.0, orgs.utilization().get(0).occupancyPercent());
        assertEquals(10L, orgs.utilization().get(0).members());
        assertEquals(200L, orgs.conversion().freeCourseUsers());
        assertEquals(33L, orgs.conversion().paidCourseUsers());
        assertEquals(16.5, orgs.conversion().conversionPercent());
    }

    @Test
    void omitsComparisonForOpenRange() {
        GrowthMetricsResponse r = service.getGrowthMetrics(MetricsRange.ALL_TIME);

        assertEquals(null, r.users().previousNewInRange());
        assertEquals(null, r.engagement().previousActiveInRange());
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
    void handlesEmptyRevenueWithoutDivisionByZero() {
        when(repository.sumPaidAmountBetween(any(), any())).thenReturn(BigDecimal.ZERO);
        when(repository.countPaidBetween(any(), any())).thenReturn(0L);

        GrowthMetricsResponse r = service.getGrowthMetrics(range);

        assertEquals(0, BigDecimal.ZERO.compareTo(r.revenue().averageTicket()));
        assertEquals(0.0, r.revenue().refundRatePercent());
    }
}
