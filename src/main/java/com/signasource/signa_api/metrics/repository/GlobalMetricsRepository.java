package com.signasource.signa_api.metrics.repository;

import com.signasource.signa_api.learning.entity.LessonBlockAttempt;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Read-only aggregates behind the global usage dashboard: the same shape as {@code
 * OrganizationMetricsRepository} but with no organization filter, so every user and course counts
 * (self-enrolled and organization alike). Grouped queries return {@code Object[]} rows mapped in
 * {@code GlobalMetricsService}; state counts are bounded by {@code until}, happenings by {@code
 * [from, until)}. Verified against H2 in {@code GlobalMetricsRepositoryTest}.
 */
public interface GlobalMetricsRepository extends Repository<LessonBlockAttempt, UUID> {

    @Query("SELECT COUNT(c) FROM Course c")
    long countCourses();

    /** Rows of {@code [VersionStatus status, long count]}. */
    @Query("SELECT v.status, COUNT(v) FROM CourseVersion v GROUP BY v.status")
    List<Object[]> countVersionsByStatus();

    /**
     * Rows of {@code [EnrollmentStatus status, long count]} for enrollments started before {@code
     * until}.
     */
    @Query(
            "SELECT e.status, COUNT(e) FROM UserCourseEnrollment e "
                    + "WHERE e.startedAt < :until GROUP BY e.status")
    List<Object[]> countEnrollmentsByStatus(@Param("until") Instant until);

    @Query("SELECT COUNT(u) FROM User u WHERE u.enabled = true")
    long countEnabledUsers();

    @Query(
            "SELECT COUNT(DISTINCT a.user.id) FROM UserActivity a "
                    + "WHERE a.occurredAt >= :from AND a.occurredAt < :until")
    long countActiveUsers(@Param("from") Instant from, @Param("until") Instant until);

    @Query(
            "SELECT COUNT(p) FROM UserLessonProgress p "
                    + "WHERE p.status = com.signasource.signa_api.learning.entity.ProgressStatus.COMPLETED "
                    + "AND p.completedAt >= :from AND p.completedAt < :until")
    long countLessonsCompletedBetween(@Param("from") Instant from, @Param("until") Instant until);

    @Query(
            "SELECT COUNT(tp) FROM UserTopicProgress tp "
                    + "WHERE tp.status = com.signasource.signa_api.learning.entity.ProgressStatus.COMPLETED "
                    + "AND tp.completedAt >= :from AND tp.completedAt < :until")
    long countTopicsCompletedBetween(@Param("from") Instant from, @Param("until") Instant until);

    @Query(
            "SELECT COUNT(a) FROM LessonBlockAttempt a "
                    + "WHERE a.isCorrect IS NOT NULL AND a.attemptedAt >= :from AND a.attemptedAt < :until")
    long countEvaluatedAttemptsBetween(@Param("from") Instant from, @Param("until") Instant until);

    @Query(
            "SELECT COUNT(a) FROM LessonBlockAttempt a "
                    + "WHERE a.isCorrect = true AND a.attemptedAt >= :from AND a.attemptedAt < :until")
    long countCorrectAttemptsBetween(@Param("from") Instant from, @Param("until") Instant until);

    /** Rows of {@code [BlockType type, long evaluated, long correct]}. */
    @Query(
            "SELECT a.lessonBlock.type, COUNT(a), "
                    + "SUM(CASE WHEN a.isCorrect = true THEN 1L ELSE 0L END) "
                    + "FROM LessonBlockAttempt a "
                    + "WHERE a.isCorrect IS NOT NULL AND a.attemptedAt >= :from AND a.attemptedAt < :until "
                    + "GROUP BY a.lessonBlock.type")
    List<Object[]> accuracyByBlockType(@Param("from") Instant from, @Param("until") Instant until);

    /** Rows of {@code [ReportStatus status, long count]} (current state, not period-bounded). */
    @Query("SELECT r.status, COUNT(r) FROM SignReport r GROUP BY r.status")
    List<Object[]> countSignReportsByStatus();

    @Query(
            "SELECT COUNT(r) FROM ContentImportRun r "
                    + "WHERE r.status = com.signasource.signa_api.content.entity.ContentImportStatus.FAILED "
                    + "AND r.startedAt >= :from AND r.startedAt < :until")
    long countFailedImportsBetween(@Param("from") Instant from, @Param("until") Instant until);

    /**
     * Daily buckets of {@code [LocalDate day, long evaluated, long correct]} (Argentina day via
     * caller's data).
     */
    @Query(
            "SELECT CAST(a.attemptedAt AS LocalDate), COUNT(a), "
                    + "SUM(CASE WHEN a.isCorrect = true THEN 1L ELSE 0L END) "
                    + "FROM LessonBlockAttempt a "
                    + "WHERE a.isCorrect IS NOT NULL AND a.attemptedAt >= :from AND a.attemptedAt < :until "
                    + "GROUP BY CAST(a.attemptedAt AS LocalDate)")
    List<Object[]> dailyAttempts(@Param("from") Instant from, @Param("until") Instant until);

    /** Daily buckets of {@code [LocalDate day, long count]} of lessons completed. */
    @Query(
            "SELECT CAST(p.completedAt AS LocalDate), COUNT(p) FROM UserLessonProgress p "
                    + "WHERE p.status = com.signasource.signa_api.learning.entity.ProgressStatus.COMPLETED "
                    + "AND p.completedAt >= :from AND p.completedAt < :until "
                    + "GROUP BY CAST(p.completedAt AS LocalDate)")
    List<Object[]> dailyLessonsCompleted(
            @Param("from") Instant from, @Param("until") Instant until);
}
