package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.UserLessonProgress;
import com.signasource.signa_api.learning.repository.projection.TopicCompletedCountView;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserLessonProgressRepository extends JpaRepository<UserLessonProgress, UUID> {

    List<UserLessonProgress> findByUserId(UUID userId);

    Optional<UserLessonProgress> findByUserIdAndLessonId(UUID userId, UUID lessonId);

    List<UserLessonProgress> findByUserIdAndLessonTopicId(UUID userId, UUID topicId);

    @Query(
            "SELECT l.topic.id AS topicId, COUNT(p.id) AS completedLessons "
                    + "FROM UserLessonProgress p JOIN p.lesson l "
                    + "WHERE p.user.id = :userId "
                    + "AND p.status = com.signasource.signa_api.learning.entity.ProgressStatus.COMPLETED "
                    + "AND l.topic.courseVersion.id IN :versionIds "
                    + "GROUP BY l.topic.id")
    List<TopicCompletedCountView> findCompletedLessonCountsByTopic(
            @Param("userId") UUID userId, @Param("versionIds") Collection<UUID> versionIds);
}
