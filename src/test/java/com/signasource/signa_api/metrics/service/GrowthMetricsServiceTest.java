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
                .thenReturn(List.<Object[]>of(new Object[] {"ACTIVE", 3L}));
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
