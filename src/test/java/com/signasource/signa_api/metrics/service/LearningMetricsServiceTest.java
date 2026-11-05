package com.signasource.signa_api.metrics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.metrics.dto.LearningMetricsResponse;
import com.signasource.signa_api.metrics.repository.LearningMetricsRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LearningMetricsServiceTest {

    @Mock private LearningMetricsRepository repository;
    @InjectMocks private LearningMetricsService service;

    private final MetricsRange range =
            MetricsRange.of(
                    Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-08T00:00:00Z"));
    private final String courseId = UUID.randomUUID().toString();
    private final String lessonA = UUID.randomUUID().toString();
    private final String lessonB = UUID.randomUUID().toString();

    @BeforeEach
    void stubs() {
        when(repository.enrollmentsByCourse(any()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {courseId, "Curso A", EnrollmentStatus.ENROLLED, 10L},
                                new Object[] {courseId, "Curso A", EnrollmentStatus.COMPLETED, 4L},
                                new Object[] {courseId, "Curso A", EnrollmentStatus.DROPPED, 2L}));
        when(repository.enrollmentsByStatus(any()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {EnrollmentStatus.ENROLLED, 10L},
                                new Object[] {EnrollmentStatus.COMPLETED, 4L}));
        when(repository.lessonAccuracy(any(), any(), anyLong()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {lessonA, "Fácil", 100L, 90L},
                                new Object[] {lessonB, "Difícil", 50L, 25L}));
        when(repository.countLearnedSignsBetween(any(), any())).thenReturn(120L);
        when(repository.dailyLearnedSigns(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {LocalDate.of(2026, 11, 2), 15L}));
        when(repository.resolvedReportTimes())
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {
                                    LocalDateTime.of(2026, 11, 1, 0, 0),
                                    LocalDateTime.of(2026, 11, 1, 2, 0)
                                },
                                new Object[] {
                                    LocalDateTime.of(2026, 11, 1, 0, 0),
                                    LocalDateTime.of(2026, 11, 1, 4, 0)
                                }));
        when(repository.mostReportedSigns())
                .thenReturn(
                        List.<Object[]>of(new Object[] {"hola", 5L}, new Object[] {"chau", 3L}));
        when(repository.importsByStatus(any(), any()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {"SUCCESS", 3L}, new Object[] {"FAILED", 1L}));
    }

    @Test
    void foldsPerCourseEnrollmentsAndCompletion() {
        LearningMetricsResponse r = service.getLearningMetrics(range);

        assertEquals(1, r.courses().size());
        LearningMetricsResponse.CourseStat course = r.courses().get(0);
        assertEquals("Curso A", course.courseName());
        assertEquals(10L, course.enrolled());
        assertEquals(4L, course.completed());
        assertEquals(16L, course.total());
        assertEquals(25.0, course.completionPercent());
    }

    @Test
    void ranksHardestLessonsByAccuracyAscending() {
        LearningMetricsResponse r = service.getLearningMetrics(range);

        assertEquals("Difícil", r.hardestLessons().get(0).lessonName());
        assertEquals(50.0, r.hardestLessons().get(0).accuracyPercent());
        assertEquals("Fácil", r.hardestLessons().get(1).lessonName());
    }

    @Test
    void averagesReportResolutionAndTakesMostReported() {
        LearningMetricsResponse r = service.getLearningMetrics(range);

        assertEquals(2L, r.reports().resolvedCount());
        assertEquals(3.0, r.reports().averageResolutionHours());
        assertEquals("hola", r.reports().mostReported().get(0).meaning());
        assertEquals(5L, r.reports().mostReported().get(0).count());
    }

    @Test
    void surfacesVocabularyAndImports() {
        LearningMetricsResponse r = service.getLearningMetrics(range);

        assertEquals(120L, r.learnedSigns());
        assertEquals(1, r.learnedSignsDaily().size());
        assertEquals(2, r.enrollmentFunnel().size());
        assertEquals("SUCCESS", r.imports().get(0).key());
    }

    @Test
    void handlesNoResolvedReports() {
        when(repository.resolvedReportTimes()).thenReturn(List.of());

        LearningMetricsResponse r = service.getLearningMetrics(range);

        assertEquals(0L, r.reports().resolvedCount());
        assertEquals(0.0, r.reports().averageResolutionHours());
    }
}
