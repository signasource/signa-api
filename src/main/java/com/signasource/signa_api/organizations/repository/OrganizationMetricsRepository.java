package com.signasource.signa_api.organizations.repository;

import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.LessonBlockAttempt;
import com.signasource.signa_api.learning.entity.UserTopicProgress;
import com.signasource.signa_api.organizations.repository.projection.AttemptTotalsView;
import com.signasource.signa_api.organizations.repository.projection.DailyAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.TopicAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.TopicStatusCountView;
import com.signasource.signa_api.organizations.repository.projection.UserAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.UserCompletedLessonsView;
import com.signasource.signa_api.organizations.repository.projection.UserCountView;
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
 */
public interface OrganizationMetricsRepository extends Repository<LessonBlockAttempt, UUID> {

    @Query(
            "SELECT a.user.id AS userId, MAX(a.attemptedAt) AS lastActivityAt, "
                    + "SUM(CASE WHEN a.isCorrect IS NOT NULL THEN 1L ELSE 0L END) AS evaluated, "
                    + "SUM(CASE WHEN a.isCorrect = true THEN 1L ELSE 0L END) AS correct "
                    + "FROM LessonBlockAttempt a "
                    + "WHERE a.organization.id = :organizationId AND a.user.id IN :userIds "
                    + "GROUP BY a.user.id")
    List<UserAttemptStatsView> findAttemptStatsByUser(
            @Param("organizationId") UUID organizationId,
            @Param("userIds") Collection<UUID> userIds);

    @Query(
            "SELECT a.lessonBlock.lesson.topic.id AS topicId, COUNT(a.id) AS evaluated, "
                    + "SUM(CASE WHEN a.isCorrect = true THEN 1L ELSE 0L END) AS correct "
                    + "FROM LessonBlockAttempt a "
                    + "WHERE a.organization.id = :organizationId AND a.user.id IN :userIds "
                    + "AND a.isCorrect IS NOT NULL "
                    + "GROUP BY a.lessonBlock.lesson.topic.id")
    List<TopicAttemptStatsView> findAttemptStatsByTopic(
            @Param("organizationId") UUID organizationId,
            @Param("userIds") Collection<UUID> userIds);

    @Query(
            "SELECT COUNT(a.id) AS evaluated, "
                    + "COALESCE(SUM(CASE WHEN a.isCorrect = true THEN 1L ELSE 0L END), 0L) AS correct "
                    + "FROM LessonBlockAttempt a "
                    + "WHERE a.organization.id = :organizationId AND a.user.id IN :userIds "
                    + "AND a.isCorrect IS NOT NULL AND a.lessonBlock.type IN :types")
    AttemptTotalsView findAttemptTotalsByBlockTypes(
            @Param("organizationId") UUID organizationId,
            @Param("userIds") Collection<UUID> userIds,
            @Param("types") Collection<BlockType> types);

    @Query(
            "SELECT CAST(a.attemptedAt AS LocalDate) AS day, COUNT(a.id) AS evaluated, "
                    + "SUM(CASE WHEN a.isCorrect = true THEN 1L ELSE 0L END) AS correct "
                    + "FROM LessonBlockAttempt a "
                    + "WHERE a.organization.id = :organizationId AND a.user.id IN :userIds "
                    + "AND a.isCorrect IS NOT NULL AND a.attemptedAt >= :since "
                    + "GROUP BY CAST(a.attemptedAt AS LocalDate)")
    List<DailyAttemptStatsView> findDailyAttemptStats(
            @Param("organizationId") UUID organizationId,
            @Param("userIds") Collection<UUID> userIds,
            @Param("since") Instant since);

    @Query(
            "SELECT p.user.id AS userId, COUNT(p.id) AS completedLessons "
                    + "FROM UserLessonProgress p "
                    + "WHERE p.user.id IN :userIds "
                    + "AND p.status = com.signasource.signa_api.learning.entity.ProgressStatus.COMPLETED "
                    + "AND p.lesson.topic.courseVersion.id IN :versionIds "
                    + "GROUP BY p.user.id")
    List<UserCompletedLessonsView> findCompletedLessonCountsByUser(
            @Param("userIds") Collection<UUID> userIds,
            @Param("versionIds") Collection<UUID> versionIds);

    @Query(
            "SELECT p.lesson.topic.courseVersion.id AS versionId, COUNT(p.id) AS total "
                    + "FROM UserLessonProgress p "
                    + "WHERE p.user.id = :userId "
                    + "AND p.status = com.signasource.signa_api.learning.entity.ProgressStatus.COMPLETED "
                    + "AND p.lesson.topic.courseVersion.id IN :versionIds "
                    + "GROUP BY p.lesson.topic.courseVersion.id")
    List<VersionCountView> findCompletedLessonCountsByVersion(
            @Param("userId") UUID userId, @Param("versionIds") Collection<UUID> versionIds);

    @Query(
            "SELECT tp.topic.id AS topicId, tp.status AS status, COUNT(tp.id) AS users "
                    + "FROM UserTopicProgress tp "
                    + "WHERE tp.user.id IN :userIds AND tp.topic.courseVersion.id IN :versionIds "
                    + "GROUP BY tp.topic.id, tp.status")
    List<TopicStatusCountView> findTopicStatusCounts(
            @Param("userIds") Collection<UUID> userIds,
            @Param("versionIds") Collection<UUID> versionIds);

    @Query(
            "SELECT tp FROM UserTopicProgress tp JOIN FETCH tp.user JOIN FETCH tp.topic "
                    + "WHERE tp.user.id IN :userIds "
                    + "AND tp.status = com.signasource.signa_api.learning.entity.ProgressStatus.IN_PROGRESS "
                    + "AND tp.topic.courseVersion.id IN :versionIds "
                    + "ORDER BY tp.startedAt DESC")
    List<UserTopicProgress> findInProgressTopics(
            @Param("userIds") Collection<UUID> userIds,
            @Param("versionIds") Collection<UUID> versionIds);

    @Query(
            "SELECT d.user.id AS userId, SUM(d.minutes) AS total FROM UserDailyActivity d "
                    + "WHERE d.user.id IN :userIds GROUP BY d.user.id")
    List<UserCountView> findLearningMinutesByUser(@Param("userIds") Collection<UUID> userIds);

    @Query(
            "SELECT d.user.id AS userId, COUNT(d.id) AS total FROM UserDailyActivity d "
                    + "WHERE d.user.id IN :userIds AND d.activityDate >= :since AND d.minutes > 0 "
                    + "GROUP BY d.user.id")
    List<UserCountView> findActiveDaysByUser(
            @Param("userIds") Collection<UUID> userIds, @Param("since") LocalDate since);

    @Query("SELECT s.currentStreak FROM UserStats s WHERE s.user.id IN :userIds")
    List<Integer> findCurrentStreaks(@Param("userIds") Collection<UUID> userIds);
}
