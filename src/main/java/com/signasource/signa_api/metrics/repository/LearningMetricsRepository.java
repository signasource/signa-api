package com.signasource.signa_api.metrics.repository;

import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import java.time.Instant;
import java.util.Collection;
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

    // ── Lesson flow (started vs completed) ───────────────────────────────────────────────────────
    @Query(
            "SELECT COUNT(p) FROM UserLessonProgress p "
                    + "WHERE p.startedAt >= :from AND p.startedAt < :until")
    long countLessonsStartedBetween(@Param("from") Instant from, @Param("until") Instant until);

    @Query(
            "SELECT COUNT(p) FROM UserLessonProgress p "
                    + "WHERE p.completedAt >= :from AND p.completedAt < :until")
    long countLessonsCompletedBetween(@Param("from") Instant from, @Param("until") Instant until);

    /**
     * Per-exercise (lesson block) evaluated attempts in the window: {@code [UUID blockId, String
     * lessonName, BlockType type, long evaluated, long correct, long distinctUsers, String
     * config]}. The 7th column ({@code MIN(config)}) is the block's JSON config — all rows in the
     * group share the same blockId so MIN returns the config deterministically. The service uses it
     * to extract the sign name label and derive the error-rate / retries rankings.
     */
    @Query(
            "SELECT a.lessonBlock.id, a.lessonBlock.lesson.name, a.lessonBlock.type, COUNT(a), "
                    + "SUM(CASE WHEN a.isCorrect = true THEN 1L ELSE 0L END), "
                    + "COUNT(DISTINCT a.user.id), "
                    + "MIN(a.lessonBlock.config) "
                    + "FROM LessonBlockAttempt a "
                    + "WHERE a.isCorrect IS NOT NULL AND a.attemptedAt >= :from AND a.attemptedAt < :until "
                    + "GROUP BY a.lessonBlock.id, a.lessonBlock.lesson.name, a.lessonBlock.type")
    List<Object[]> exerciseStats(@Param("from") Instant from, @Param("until") Instant until);

    /**
     * Per-lesson progress as of {@code until}: {@code [UUID lessonId, String name, long started,
     * long completed]}.
     */
    @Query(
            "SELECT p.lesson.id, p.lesson.name, COUNT(p), "
                    + "SUM(CASE WHEN p.status = com.signasource.signa_api.learning.entity.ProgressStatus.COMPLETED THEN 1L ELSE 0L END) "
                    + "FROM UserLessonProgress p "
                    + "WHERE p.startedAt IS NOT NULL AND p.startedAt < :until "
                    + "GROUP BY p.lesson.id, p.lesson.name")
    List<Object[]> lessonDropoffStats(@Param("until") Instant until);

    /**
     * Rows of {@code [LocalDate day, long evaluated, long correct]} for the correct-answer
     * evolution.
     */
    @Query(
            "SELECT CAST(a.attemptedAt AS LocalDate), COUNT(a), "
                    + "SUM(CASE WHEN a.isCorrect = true THEN 1L ELSE 0L END) "
                    + "FROM LessonBlockAttempt a "
                    + "WHERE a.isCorrect IS NOT NULL AND a.attemptedAt >= :from AND a.attemptedAt < :until "
                    + "GROUP BY CAST(a.attemptedAt AS LocalDate)")
    List<Object[]> dailyAccuracy(@Param("from") Instant from, @Param("until") Instant until);

    // ── Onboarding funnel (distinct users reaching each step, as of until)
    // ────────────────────────
    @Query("SELECT COUNT(u) FROM User u WHERE u.enabled = true AND u.createdAt < :until")
    long countUsersBefore(@Param("until") Instant until);

    @Query(
            "SELECT COUNT(DISTINCT p.user.id) FROM UserLessonProgress p "
                    + "WHERE p.startedAt IS NOT NULL AND p.startedAt < :until")
    long countUsersStartedLessonBefore(@Param("until") Instant until);

    @Query(
            "SELECT COUNT(DISTINCT a.user.id) FROM LessonBlockAttempt a "
                    + "WHERE a.attemptedAt < :until")
    long countUsersAttemptedBefore(@Param("until") Instant until);

    @Query(
            "SELECT COUNT(DISTINCT p.user.id) FROM UserLessonProgress p "
                    + "WHERE p.status = com.signasource.signa_api.learning.entity.ProgressStatus.COMPLETED "
                    + "AND p.completedAt < :until")
    long countUsersCompletedLessonBefore(@Param("until") Instant until);

    /** User ids with 2+ completed lessons as of {@code until}; the service counts the rows. */
    @Query(
            "SELECT p.user.id FROM UserLessonProgress p "
                    + "WHERE p.status = com.signasource.signa_api.learning.entity.ProgressStatus.COMPLETED "
                    + "AND p.completedAt < :until "
                    + "GROUP BY p.user.id HAVING COUNT(p) >= 2")
    List<UUID> usersWithTwoPlusCompletedLessons(@Param("until") Instant until);

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

    /**
     * Camera-block attempt rows for ML metrics: {@code [UUID blockId, BlockType type, long
     * evaluated, long correct, String config]}. Covers PERFORM_SIGN and SPELL_NAME blocks only
     * (single-sign types that map cleanly to one sign per attempt). VISUAL_RECOGNITION is excluded
     * because its signSequence is multi-sign and cannot be attributed to a single sign.
     */
    @Query(
            "SELECT a.lessonBlock.id, a.lessonBlock.type, COUNT(a), "
                    + "SUM(CASE WHEN a.isCorrect = true THEN 1L ELSE 0L END), "
                    + "MIN(a.lessonBlock.config) "
                    + "FROM LessonBlockAttempt a "
                    + "WHERE a.isCorrect IS NOT NULL AND a.attemptedAt >= :from AND a.attemptedAt < :until "
                    + "AND a.lessonBlock.type IN "
                    + "(com.signasource.signa_api.learning.entity.BlockType.PERFORM_SIGN, "
                    + "com.signasource.signa_api.learning.entity.BlockType.SPELL_NAME) "
                    + "GROUP BY a.lessonBlock.id, a.lessonBlock.type")
    List<Object[]> cameraBlockStats(@Param("from") Instant from, @Param("until") Instant until);

    // ── Course-filtered variants (lesson → topic → courseVersion → course path) ─────────────────

    @Query(
            "SELECT a.lessonBlock.id, a.lessonBlock.lesson.name, a.lessonBlock.type, COUNT(a), "
                    + "SUM(CASE WHEN a.isCorrect = true THEN 1L ELSE 0L END), "
                    + "COUNT(DISTINCT a.user.id), "
                    + "MIN(a.lessonBlock.config) "
                    + "FROM LessonBlockAttempt a "
                    + "WHERE a.isCorrect IS NOT NULL AND a.attemptedAt >= :from AND a.attemptedAt < :until "
                    + "AND a.lessonBlock.lesson.topic.courseVersion.course.id IN :courseIds "
                    + "GROUP BY a.lessonBlock.id, a.lessonBlock.lesson.name, a.lessonBlock.type")
    List<Object[]> exerciseStatsByCourse(
            @Param("from") Instant from,
            @Param("until") Instant until,
            @Param("courseIds") Collection<UUID> courseIds);

    @Query(
            "SELECT a.lessonBlock.lesson.id, a.lessonBlock.lesson.name, COUNT(a), "
                    + "SUM(CASE WHEN a.isCorrect = true THEN 1L ELSE 0L END) "
                    + "FROM LessonBlockAttempt a "
                    + "WHERE a.isCorrect IS NOT NULL AND a.attemptedAt >= :from AND a.attemptedAt < :until "
                    + "AND a.lessonBlock.lesson.topic.courseVersion.course.id IN :courseIds "
                    + "GROUP BY a.lessonBlock.lesson.id, a.lessonBlock.lesson.name "
                    + "HAVING COUNT(a) >= :minEvaluated")
    List<Object[]> lessonAccuracyByCourse(
            @Param("from") Instant from,
            @Param("until") Instant until,
            @Param("minEvaluated") long minEvaluated,
            @Param("courseIds") Collection<UUID> courseIds);

    @Query(
            "SELECT COUNT(p) FROM UserLessonProgress p "
                    + "WHERE p.startedAt >= :from AND p.startedAt < :until "
                    + "AND p.lesson.topic.courseVersion.course.id IN :courseIds")
    long countLessonsStartedByCourse(
            @Param("from") Instant from,
            @Param("until") Instant until,
            @Param("courseIds") Collection<UUID> courseIds);

    @Query(
            "SELECT COUNT(p) FROM UserLessonProgress p "
                    + "WHERE p.completedAt >= :from AND p.completedAt < :until "
                    + "AND p.lesson.topic.courseVersion.course.id IN :courseIds")
    long countLessonsCompletedByCourse(
            @Param("from") Instant from,
            @Param("until") Instant until,
            @Param("courseIds") Collection<UUID> courseIds);

    @Query(
            "SELECT p.lesson.id, p.lesson.name, COUNT(p), "
                    + "SUM(CASE WHEN p.status = com.signasource.signa_api.learning.entity.ProgressStatus.COMPLETED THEN 1L ELSE 0L END) "
                    + "FROM UserLessonProgress p "
                    + "WHERE p.startedAt IS NOT NULL AND p.startedAt < :until "
                    + "AND p.lesson.topic.courseVersion.course.id IN :courseIds "
                    + "GROUP BY p.lesson.id, p.lesson.name")
    List<Object[]> lessonDropoffStatsByCourse(
            @Param("until") Instant until, @Param("courseIds") Collection<UUID> courseIds);

    @Query(
            "SELECT CAST(a.attemptedAt AS LocalDate), COUNT(a), "
                    + "SUM(CASE WHEN a.isCorrect = true THEN 1L ELSE 0L END) "
                    + "FROM LessonBlockAttempt a "
                    + "WHERE a.isCorrect IS NOT NULL AND a.attemptedAt >= :from AND a.attemptedAt < :until "
                    + "AND a.lessonBlock.lesson.topic.courseVersion.course.id IN :courseIds "
                    + "GROUP BY CAST(a.attemptedAt AS LocalDate)")
    List<Object[]> dailyAccuracyByCourse(
            @Param("from") Instant from,
            @Param("until") Instant until,
            @Param("courseIds") Collection<UUID> courseIds);

    // ── Contenido a revisar ──────────────────────────────────────────────────────────────────────
    /**
     * Per-lesson progress as of {@code until} with its course: {@code [UUID lessonId, String
     * lessonName, UUID courseId, String courseName, long started, long completed]}.
     */
    @Query(
            "SELECT p.lesson.id, p.lesson.name, c.id, c.name, COUNT(p), "
                    + "SUM(CASE WHEN p.status = com.signasource.signa_api.learning.entity.ProgressStatus.COMPLETED THEN 1L ELSE 0L END) "
                    + "FROM UserLessonProgress p JOIN p.lesson.topic.courseVersion.course c "
                    + "WHERE p.startedAt IS NOT NULL AND p.startedAt < :until "
                    + "GROUP BY p.lesson.id, p.lesson.name, c.id, c.name")
    List<Object[]> lessonReviewStats(@Param("until") Instant until);

    @Query(
            "SELECT p.lesson.id, p.lesson.name, c.id, c.name, COUNT(p), "
                    + "SUM(CASE WHEN p.status = com.signasource.signa_api.learning.entity.ProgressStatus.COMPLETED THEN 1L ELSE 0L END) "
                    + "FROM UserLessonProgress p JOIN p.lesson.topic.courseVersion.course c "
                    + "WHERE p.startedAt IS NOT NULL AND p.startedAt < :until AND c.id IN :courseIds "
                    + "GROUP BY p.lesson.id, p.lesson.name, c.id, c.name")
    List<Object[]> lessonReviewStatsByCourse(
            @Param("until") Instant until, @Param("courseIds") Collection<UUID> courseIds);

    /** Rows of {@code [UUID lessonId, String config]}: every block config of the given lessons. */
    @Query("SELECT b.lesson.id, b.config FROM LessonBlock b WHERE b.lesson.id IN :lessonIds")
    List<Object[]> blockConfigsOfLessons(@Param("lessonIds") Collection<UUID> lessonIds);

    /** Rows of {@code [String meaning, long count]} of unresolved reports, most reported first. */
    @Query(
            "SELECT r.sign.meaning, COUNT(r) FROM SignReport r "
                    + "WHERE r.status = com.signasource.signa_api.learning.entity.ReportStatus.PENDING "
                    + "GROUP BY r.sign.meaning ORDER BY COUNT(r) DESC")
    List<Object[]> pendingReportsBySign();
}
