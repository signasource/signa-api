package com.signasource.signa_api.metrics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.learning.entity.BlockType;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LearningMetricsServiceTest {

    @Mock private LearningMetricsRepository repository;
    private LearningMetricsService service;

    private final MetricsRange range =
            MetricsRange.of(
                    Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-08T00:00:00Z"));
    private final String courseId = UUID.randomUUID().toString();
    private final String lessonA = UUID.randomUUID().toString();
    private final String lessonB = UUID.randomUUID().toString();

    @BeforeEach
    void stubs() {
        service = new LearningMetricsService(repository, new ObjectMapper());
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
    void omitsPeriodOverPeriodComparisonForOpenRange() {
        LearningMetricsResponse r = service.getLearningMetrics(MetricsRange.ALL_TIME);

        assertEquals(null, r.lessons().previousStarted());
        assertEquals(null, r.lessons().previousCompleted());
        assertEquals(null, r.exercises().previousAttempted());
        assertEquals(null, r.exercises().previousCorrect());
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
    void buildsLessonFlowExerciseStatsRankingsAndFunnel() {
        when(repository.countLessonsStartedBetween(any(), any())).thenReturn(100L);
        when(repository.countLessonsCompletedBetween(any(), any())).thenReturn(40L);
        when(repository.exerciseStats(any(), any()))
                .thenReturn(
                        List.<Object[]>of(
                                // [blockId, lessonName, type, evaluated, correct, distinctUsers,
                                // config]
                                new Object[] {
                                    "b1",
                                    "Fácil",
                                    BlockType.SELECT_MEANING,
                                    100L,
                                    90L,
                                    50L,
                                    "{\"sign\":\"seña_facil\"}"
                                },
                                new Object[] {
                                    "b2",
                                    "Difícil",
                                    BlockType.VISUAL_RECOGNITION,
                                    100L,
                                    20L,
                                    25L,
                                    "{\"signSequence\":[\"seña_dificil\"]}"
                                },
                                new Object[] {
                                    "b3",
                                    "Rara",
                                    BlockType.MATCH,
                                    5L,
                                    5L,
                                    5L,
                                    "{\"concepts\":[\"seña_rara\"]}"
                                })); // below floor (5 < EXERCISE_MIN_EVALUATED=3? No, 5>=3 so
        // included)
        when(repository.lessonDropoffStats(any()))
                .thenReturn(
                        List.<Object[]>of(
                                new Object[] {lessonA, "Abandonada", 100L, 40L},
                                new Object[] {lessonB, "Sólida", 50L, 45L},
                                new Object[] {"lc", "Poca muestra", 3L, 0L})); // below floor
        when(repository.countUsersBefore(any())).thenReturn(100L);
        when(repository.countUsersStartedLessonBefore(any())).thenReturn(80L);
        when(repository.countUsersAttemptedBefore(any())).thenReturn(65L);
        when(repository.countUsersCompletedLessonBefore(any())).thenReturn(48L);
        when(repository.usersWithTwoPlusCompletedLessons(any()))
                .thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));
        when(repository.dailyAccuracy(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[] {LocalDate.of(2026, 11, 2), 80L, 60L}));

        LearningMetricsResponse r = service.getLearningMetrics(range);

        assertEquals(100L, r.lessons().started());
        assertEquals(40L, r.lessons().completed());
        assertEquals(40.0, r.lessons().completionPercent());

        // Headline includes every evaluable block (100+100+5 attempts, 90+20+5 correct).
        assertEquals(205L, r.exercises().attempted());
        assertEquals(115L, r.exercises().correct());
        assertEquals(56.1, r.exercises().successPercent());
        assertEquals(2.6, r.exercises().averageAttempts()); // 205 / (50+25+5)

        // Closed range → previous equal-length window is comparable (same stubbed counts).
        assertEquals(100L, r.lessons().previousStarted());
        assertEquals(40L, r.lessons().previousCompleted());
        assertEquals(205L, r.exercises().previousAttempted());
        assertEquals(115L, r.exercises().previousCorrect());

        // Rankings: b3 (5 evaluated) is above the floor of 3, so all 3 blocks appear.
        assertEquals(3, r.highestErrorExercises().size());
        assertEquals(80.0, r.highestErrorExercises().get(0).errorPercent());
        // Label comes from signSequence extracted from VISUAL_RECOGNITION config.
        assertEquals("seña_dificil", r.highestErrorExercises().get(0).label());
        assertEquals(4.0, r.mostRetriedExercises().get(0).averageAttempts());

        // "Poca muestra" (3 started) is above the floor of 2 → 3 lessons ranked.
        assertEquals(3, r.lessonRanking().size());
        // 100% abandonment (0/3 completed) sorts first.
        assertEquals("Poca muestra", r.lessonRanking().get(0).lessonName());
        assertEquals(100.0, r.lessonRanking().get(0).abandonmentPercent());

        // hardestSigns: only SELECT_MEANING blocks contribute (b1: sign=seña_facil, 10% error).
        assertEquals(1, r.hardestSigns().size());
        assertEquals("seña_facil", r.hardestSigns().get(0).sign());
        assertEquals(10.0, r.hardestSigns().get(0).errorPercent());

        assertEquals(5, r.onboardingFunnel().size());
        assertEquals("Usuarios registrados", r.onboardingFunnel().get(0).key());
        assertEquals(100L, r.onboardingFunnel().get(0).count());
        assertEquals(2L, r.onboardingFunnel().get(4).count());

        assertEquals(1, r.dailyAccuracy().size());
        assertEquals(75.0, r.dailyAccuracy().get(0).accuracyPercent());
    }

    @Test
    void handlesNoResolvedReports() {
        when(repository.resolvedReportTimes()).thenReturn(List.of());

        LearningMetricsResponse r = service.getLearningMetrics(range);

        assertEquals(0L, r.reports().resolvedCount());
        assertEquals(0.0, r.reports().averageResolutionHours());
    }
}
