package com.signasource.signa_api.metrics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.ReportStatus;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.metrics.dto.BusinessMetricsResponse;
import com.signasource.signa_api.metrics.repository.GlobalMetricsRepository;
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
class GlobalMetricsServiceTest {

    @Mock private GlobalMetricsRepository repository;
    @InjectMocks private GlobalMetricsService service;

    private final MetricsRange range =
            MetricsRange.of(
                    Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-08T00:00:00Z"));

    @BeforeEach
    void stubs() {
        when(repository.countCourses()).thenReturn(3L);
        when(repository.countVersionsByStatus())
                .thenReturn(
                        List.of(
                                new Object[] {VersionStatus.PUBLISHED, 2L},
                                new Object[] {VersionStatus.DRAFT, 1L}));
        when(repository.countEnrollmentsByStatus(any()))
                .thenReturn(
                        List.of(
                                new Object[] {EnrollmentStatus.ENROLLED, 10L},
                                new Object[] {EnrollmentStatus.COMPLETED, 4L}));
        when(repository.countEnabledUsers()).thenReturn(50L);
        when(repository.countActiveUsers(any(), any())).thenReturn(12L);
        when(repository.countLessonsCompletedBetween(any(), any())).thenReturn(30L);
        when(repository.countTopicsCompletedBetween(any(), any())).thenReturn(5L);
        when(repository.countEvaluatedAttemptsBetween(any(), any())).thenReturn(200L);
        when(repository.countCorrectAttemptsBetween(any(), any())).thenReturn(150L);
        when(repository.accuracyByBlockType(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {BlockType.SELECT_MEANING, 100L, 80L}));
        when(repository.countSignReportsByStatus())
                .thenReturn(
                        List.of(
                                new Object[] {ReportStatus.PENDING, 7L},
                                new Object[] {ReportStatus.RESOLVED, 3L}));
        when(repository.countFailedImportsBetween(any(), any())).thenReturn(1L);
        when(repository.dailyAttempts(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {LocalDate.of(2026, 11, 2), 40L, 30L}));
        when(repository.dailyLessonsCompleted(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {LocalDate.of(2026, 11, 2), 6L}));
    }

    @Test
    void assemblesSnapshot() {
        BusinessMetricsResponse response = service.getBusinessMetrics(range);

        assertEquals(3L, response.courses().total());
        assertEquals("PUBLISHED", response.courses().versionsByStatus().get(0).key());
        assertEquals(2, response.enrollments().size());
        assertEquals(30L, response.progress().lessonsCompleted());
        assertEquals(50L, response.users().total());
        assertEquals(12L, response.users().active());
    }

    @Test
    void computesAccuracyPercent() {
        BusinessMetricsResponse response = service.getBusinessMetrics(range);

        assertEquals(75.0, response.accuracy().accuracyPercent());
        assertEquals(80.0, response.accuracy().byBlockType().get(0).accuracyPercent());
    }

    @Test
    void extractsPendingReports() {
        BusinessMetricsResponse response = service.getBusinessMetrics(range);

        assertEquals(7L, response.errors().pendingSignReports());
        assertEquals(1L, response.errors().failedImports());
    }

    @Test
    void mergesDailyActivity() {
        BusinessMetricsResponse response = service.getBusinessMetrics(range);

        assertEquals(1, response.activity().size());
        BusinessMetricsResponse.DailyActivityPoint point = response.activity().get(0);
        assertEquals(40L, point.attempts());
        assertEquals(30L, point.correct());
        assertEquals(6L, point.lessonsCompleted());
    }
}
