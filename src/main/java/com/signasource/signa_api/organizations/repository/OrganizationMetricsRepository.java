package com.signasource.signa_api.organizations.repository;

import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.LessonBlockAttempt;
import com.signasource.signa_api.learning.entity.UserTopicProgress;
import com.signasource.signa_api.organizations.repository.projection.AttemptTotalsView;
import com.signasource.signa_api.organizations.repository.projection.AttemptView;
import com.signasource.signa_api.organizations.repository.projection.DailyAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.DailyMinutesView;
import com.signasource.signa_api.organizations.repository.projection.TopicAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.TopicProgressCountView;
import com.signasource.signa_api.organizations.repository.projection.UserAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.UserCompletedLessonsView;
import com.signasource.signa_api.organizations.repository.projection.UserCountView;
import com.signasource.signa_api.organizations.repository.projection.UserTopicView;
import com.signasource.signa_api.organizations.repository.projection.VersionCountView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Read-only aggregates behind the organization dashboard. Attempt-based figures only count attempts
 * tagged with the organization ({@code LessonBlockAttempt.organization}); evaluated attempts are
 * those with a correctness value, i.e. everything but INFO views.
 *
 * <p>Every query is bounded in time (see {@code MetricsPeriod}): {@code from}/{@code until} count
 * what happened in {@code [from, until)}, and a lone {@code until} measures a state as of that
 * instant. A completion with no timestamp counts as done before any {@code until}.
 */
public interface OrganizationMetricsRepository extends Repository<LessonBlockAttempt, UUID> {

    /**
     * Per user: last attempt before {@code until}, and evaluated/correct attempts in {@code [from,
     * until)}. A user with attempts only before {@code from} still appears, with zero counts.
     */
    @Query(
            "SELECT a.user.id AS userId, MAX(a.attemptedAt) AS lastActivityAt, "
                    + "SUM(CASE WHEN a.isCorrect IS NOT NULL AND a.attemptedAt >= :from "
                    + "THEN 1L ELSE 0L END) AS evaluated, "
                    + "SUM(CASE WHEN a.isCorrect = true AND a.attemptedAt >= :from "
                    + "THEN 1L ELSE 0L END) AS correct "
                    + "FROM LessonBlockAttempt a "
                    + "WHERE a.organization.id = :organizationId AND a.user.id IN :userIds "
                    + "AND a.lessonBlock.lesson.topic.courseVersion.course.id IN :courseIds "
                    + "AND a.attemptedAt < :until "
                    + "GROUP BY a.user.id")
    List<UserAttemptStatsView> findAttemptStatsByUser(
            @Param("organizationId") UUID organizationId,
            @Param("userIds") Collection<UUID> userIds,
            @Param("courseIds") Collection<UUID> courseIds,
            @Param("from") Instant from,
            @Param("until") Instant until);

    @Query(
            "SELECT a.lessonBlock.lesson.topic.id AS topicId, COUNT(a.id) AS evaluated, "
                    + "SUM(CASE WHEN a.isCorrect = true THEN 1L ELSE 0L END) AS correct "
                    + "FROM LessonBlockAttempt a "
                    + "WHERE a.organization.id = :organizationId AND a.user.id IN :userIds "
                    + "AND a.lessonBlock.lesson.topic.courseVersion.course.id IN :courseIds "
                    + "AND a.isCorrect IS NOT NULL "
                    + "AND a.attemptedAt >= :from AND a.attemptedAt < :until "
                    + "GROUP BY a.lessonBlock.lesson.topic.id")
    List<TopicAttemptStatsView> findAttemptStatsByTopic(
            @Param("organizationId") UUID organizationId,
            @Param("userIds") Collection<UUID> userIds,
            @Param("courseIds") Collection<UUID> courseIds,
            @Param("from") Instant from,
            @Param("until") Instant until);

    @Query(
            "SELECT COUNT(a.id) AS evaluated, "
                    + "COALESCE(SUM(CASE WHEN a.isCorrect = true THEN 1L ELSE 0L END), 0L) AS correct "
                    + "FROM LessonBlockAttempt a "
                    + "WHERE a.organization.id = :organizationId AND a.user.id IN :userIds "
                    + "AND a.lessonBlock.lesson.topic.courseVersion.course.id IN :courseIds "
                    + "AND a.isCorrect IS NOT NULL AND a.lessonBlock.type IN :types "
                    + "AND a.attemptedAt >= :from AND a.attemptedAt < :until")
    AttemptTotalsView findAttemptTotalsByBlockTypes(
            @Param("organizationId") UUID organizationId,
            @Param("userIds") Collection<UUID> userIds,
            @Param("courseIds") Collection<UUID> courseIds,
            @Param("types") Collection<BlockType> types,
            @Param("from") Instant from,
            @Param("until") Instant until);

    @Query(
            "SELECT CAST(a.attemptedAt AS LocalDate) AS day, COUNT(a.id) AS evaluated, "
                    + "SUM(CASE WHEN a.isCorrect = true THEN 1L ELSE 0L END) AS correct "
                    + "FROM LessonBlockAttempt a "
                    + "WHERE a.organization.id = :organizationId AND a.user.id IN :userIds "
                    + "AND a.lessonBlock.lesson.topic.courseVersion.course.id IN :courseIds "
                    + "AND a.isCorrect IS NOT NULL "
                    + "AND a.attemptedAt >= :since AND a.attemptedAt < :until "
                    + "GROUP BY CAST(a.attemptedAt AS LocalDate)")
    List<DailyAttemptStatsView> findDailyAttemptStats(
            @Param("organizationId") UUID organizationId,
            @Param("userIds") Collection<UUID> userIds,
            @Param("courseIds") Collection<UUID> courseIds,
            @Param("since") Instant since,
            @Param("until") Instant until);

    @Query(
            "SELECT p.user.id AS userId, COUNT(p.id) AS completedLessons "
                    + "FROM UserLessonProgress p "
                    + "WHERE p.user.id IN :userIds "
                    + "AND p.status = com.signasource.signa_api.learning.entity.ProgressStatus.COMPLETED "
                    + "AND (p.completedAt IS NULL OR p.completedAt < :until) "
                    + "AND p.lesson.topic.courseVersion.id IN :versionIds "
                    + "GROUP BY p.user.id")
    List<UserCompletedLessonsView> findCompletedLessonCountsByUser(
            @Param("userIds") Collection<UUID> userIds,
            @Param("versionIds") Collection<UUID> versionIds,
            @Param("until") Instant until);

    @Query(
            "SELECT p.lesson.topic.courseVersion.id AS versionId, COUNT(p.id) AS total "
                    + "FROM UserLessonProgress p "
                    + "WHERE p.user.id = :userId "
                    + "AND p.status = com.signasource.signa_api.learning.entity.ProgressStatus.COMPLETED "
                    + "AND (p.completedAt IS NULL OR p.completedAt < :until) "
                    + "AND p.lesson.topic.courseVersion.id IN :versionIds "
                    + "GROUP BY p.lesson.topic.courseVersion.id")
    List<VersionCountView> findCompletedLessonCountsByVersion(
            @Param("userId") UUID userId,
            @Param("versionIds") Collection<UUID> versionIds,
            @Param("until") Instant until);

    /**
     * People per topic as of {@code until}: who had completed it and who was on it (a topic
     * finished later still counts as in progress; one not yet started then is left out). Two sums
     * instead of grouping by a CASE: Postgres sees each bound {@code :until} as a different
     * expression and rejects that GROUP BY.
     */
    @Query(
            "SELECT tp.topic.id AS topicId, "
                    + "SUM(CASE WHEN tp.status = com.signasource.signa_api.learning.entity.ProgressStatus.COMPLETED "
                    + "AND (tp.completedAt IS NULL OR tp.completedAt < :until) "
                    + "THEN 1L ELSE 0L END) AS completed, "
                    + "SUM(CASE WHEN tp.status = com.signasource.signa_api.learning.entity.ProgressStatus.COMPLETED "
                    + "AND (tp.completedAt IS NULL OR tp.completedAt < :until) "
                    + "THEN 0L ELSE 1L END) AS inProgress "
                    + "FROM UserTopicProgress tp "
                    + "WHERE tp.user.id IN :userIds AND tp.topic.courseVersion.id IN :versionIds "
                    + "AND tp.status <> com.signasource.signa_api.learning.entity.ProgressStatus.LOCKED "
                    + "AND (tp.startedAt IS NULL OR tp.startedAt < :until) "
                    + "GROUP BY tp.topic.id")
    List<TopicProgressCountView> findTopicProgressCounts(
            @Param("userIds") Collection<UUID> userIds,
            @Param("versionIds") Collection<UUID> versionIds,
            @Param("until") Instant until);

    /** Topics completed in {@code [from, until)}, summed over users. */
    @Query(
            "SELECT COUNT(tp.id) FROM UserTopicProgress tp "
                    + "WHERE tp.user.id IN :userIds AND tp.topic.courseVersion.id IN :versionIds "
                    + "AND tp.status = com.signasource.signa_api.learning.entity.ProgressStatus.COMPLETED "
                    + "AND tp.completedAt >= :from AND tp.completedAt < :until")
    long countTopicsCompletedBetween(
            @Param("userIds") Collection<UUID> userIds,
            @Param("versionIds") Collection<UUID> versionIds,
            @Param("from") Instant from,
            @Param("until") Instant until);

    /** Topics in progress as of {@code until}, most recently started first. */
    @Query(
            "SELECT tp FROM UserTopicProgress tp JOIN FETCH tp.user JOIN FETCH tp.topic "
                    + "WHERE tp.user.id IN :userIds "
                    + "AND (tp.status = com.signasource.signa_api.learning.entity.ProgressStatus.IN_PROGRESS "
                    + "OR (tp.status = com.signasource.signa_api.learning.entity.ProgressStatus.COMPLETED "
                    + "AND tp.completedAt >= :until)) "
                    + "AND (tp.startedAt IS NULL OR tp.startedAt < :until) "
                    + "AND tp.topic.courseVersion.id IN :versionIds "
                    + "ORDER BY tp.startedAt DESC")
    List<UserTopicProgress> findInProgressTopics(
            @Param("userIds") Collection<UUID> userIds,
            @Param("versionIds") Collection<UUID> versionIds,
            @Param("until") Instant until);

    @Query(
            "SELECT d.user.id AS userId, SUM(d.minutes) AS total FROM UserDailyActivity d "
                    + "WHERE d.user.id IN :userIds AND d.activityDate BETWEEN :from AND :to "
                    + "GROUP BY d.user.id")
    List<UserCountView> findLearningMinutesByUser(
            @Param("userIds") Collection<UUID> userIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query(
            "SELECT d.user.id AS userId, COUNT(d.id) AS total FROM UserDailyActivity d "
                    + "WHERE d.user.id IN :userIds AND d.activityDate BETWEEN :from AND :to "
                    + "AND d.minutes > 0 "
                    + "GROUP BY d.user.id")
    List<UserCountView> findActiveDaysByUser(
            @Param("userIds") Collection<UUID> userIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    /** Topics completed as of {@code until}. */
    @Query(
            "SELECT tp.user.id AS userId, tp.topic.id AS topicId FROM UserTopicProgress tp "
                    + "WHERE tp.user.id IN :userIds AND tp.topic.courseVersion.id IN :versionIds "
                    + "AND tp.status = com.signasource.signa_api.learning.entity.ProgressStatus.COMPLETED "
                    + "AND (tp.completedAt IS NULL OR tp.completedAt < :until)")
    List<UserTopicView> findCompletedTopics(
            @Param("userIds") Collection<UUID> userIds,
            @Param("versionIds") Collection<UUID> versionIds,
            @Param("until") Instant until);

    /** Raw attempts in a period; the caller buckets them by local day. */
    @Query(
            "SELECT a.user.id AS userId, a.attemptedAt AS attemptedAt, a.isCorrect AS isCorrect "
                    + "FROM LessonBlockAttempt a "
                    + "WHERE a.organization.id = :organizationId AND a.user.id IN :userIds "
                    + "AND a.lessonBlock.lesson.topic.courseVersion.course.id IN :courseIds "
                    + "AND a.attemptedAt >= :from AND a.attemptedAt < :to")
    List<AttemptView> findAttemptsBetween(
            @Param("organizationId") UUID organizationId,
            @Param("userIds") Collection<UUID> userIds,
            @Param("courseIds") Collection<UUID> courseIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query(
            "SELECT p.completedAt FROM UserLessonProgress p "
                    + "WHERE p.user.id IN :userIds "
                    + "AND p.status = com.signasource.signa_api.learning.entity.ProgressStatus.COMPLETED "
                    + "AND p.lesson.topic.courseVersion.id IN :versionIds "
                    + "AND p.completedAt >= :from AND p.completedAt < :to")
    List<Instant> findLessonCompletionsBetween(
            @Param("userIds") Collection<UUID> userIds,
            @Param("versionIds") Collection<UUID> versionIds,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query(
            "SELECT d.activityDate AS day, SUM(d.minutes) AS minutes FROM UserDailyActivity d "
                    + "WHERE d.user.id IN :userIds AND d.activityDate BETWEEN :from AND :to "
                    + "GROUP BY d.activityDate")
    List<DailyMinutesView> findDailyMinutes(
            @Param("userIds") Collection<UUID> userIds,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query("SELECT s.currentStreak FROM UserStats s WHERE s.user.id IN :userIds")
    List<Integer> findCurrentStreaks(@Param("userIds") Collection<UUID> userIds);
}
