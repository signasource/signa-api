package com.signasource.signa_api.metrics.repository;

import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Read-only aggregates behind the usage dashboard's "Aprendizaje & contenido" apartado, with no
 * organization filter. Grouped queries return {@code Object[]} rows mapped in {@code
 * LearningMetricsService}; enrollment state is bounded by {@code until}, attempts/learned-signs/
 * imports by {@code [from, until)}, and sign-report figures are the current state (not windowed,
 * like the business snapshot). Verified against H2 in {@code LearningMetricsRepositoryTest}.
 */
public interface LearningMetricsRepository extends Repository<UserCourseEnrollment, UUID> {

    /** Rows of {@code [UUID courseId, String courseName, EnrollmentStatus status, long count]}. */
    @Query(
            "SELECT e.courseVersion.course.id, e.courseVersion.course.name, e.status, COUNT(e) "
                    + "FROM UserCourseEnrollment e WHERE e.startedAt < :until "
                    + "GROUP BY e.courseVersion.course.id, e.courseVersion.course.name, e.status")
    List<Object[]> enrollmentsByCourse(@Param("until") Instant until);

    /** Rows of {@code [EnrollmentStatus status, long count]} (the completion funnel). */
    @Query(
            "SELECT e.status, COUNT(e) FROM UserCourseEnrollment e "
                    + "WHERE e.startedAt < :until GROUP BY e.status")
    List<Object[]> enrollmentsByStatus(@Param("until") Instant until);

    /**
     * Rows of {@code [UUID lessonId, String lessonName, long evaluated, long correct]} for lessons
     * with at least {@code minEvaluated} evaluated attempts in the window (the dashboard ranks
     * these by accuracy ascending to surface the hardest content).
     */
    @Query(
            "SELECT a.lessonBlock.lesson.id, a.lessonBlock.lesson.name, COUNT(a), "
                    + "SUM(CASE WHEN a.isCorrect = true THEN 1L ELSE 0L END) "
                    + "FROM LessonBlockAttempt a "
                    + "WHERE a.isCorrect IS NOT NULL AND a.attemptedAt >= :from AND a.attemptedAt < :until "
                    + "GROUP BY a.lessonBlock.lesson.id, a.lessonBlock.lesson.name "
                    + "HAVING COUNT(a) >= :minEvaluated")
    List<Object[]> lessonAccuracy(
            @Param("from") Instant from,
            @Param("until") Instant until,
            @Param("minEvaluated") long minEvaluated);

    @Query(
            "SELECT COUNT(ls) FROM UserLearnedSign ls "
                    + "WHERE ls.learnedAt >= :from AND ls.learnedAt < :until")
    long countLearnedSignsBetween(@Param("from") Instant from, @Param("until") Instant until);

    /** Rows of {@code [LocalDate day, long count]} of newly learned signs. */
    @Query(
            "SELECT CAST(ls.learnedAt AS LocalDate), COUNT(ls) FROM UserLearnedSign ls "
                    + "WHERE ls.learnedAt >= :from AND ls.learnedAt < :until "
                    + "GROUP BY CAST(ls.learnedAt AS LocalDate)")
    List<Object[]> dailyLearnedSigns(@Param("from") Instant from, @Param("until") Instant until);

    /** Rows of {@code [LocalDateTime createdAt, LocalDateTime resolvedAt]} for resolved reports. */
    @Query("SELECT r.createdAt, r.resolvedAt FROM SignReport r WHERE r.resolvedAt IS NOT NULL")
    List<Object[]> resolvedReportTimes();

    /** Rows of {@code [String meaning, long count]} ordered by most reported first. */
    @Query(
            "SELECT r.sign.meaning, COUNT(r) FROM SignReport r "
                    + "GROUP BY r.sign.meaning ORDER BY COUNT(r) DESC")
    List<Object[]> mostReportedSigns();

    /** Rows of {@code [ContentImportStatus status, long count]} of import runs in the window. */
    @Query(
            "SELECT r.status, COUNT(r) FROM ContentImportRun r "
                    + "WHERE r.startedAt >= :from AND r.startedAt < :until GROUP BY r.status")
    List<Object[]> importsByStatus(@Param("from") Instant from, @Param("until") Instant until);
}
