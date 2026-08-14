package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.UserTopicProgress;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserTopicProgressRepository extends JpaRepository<UserTopicProgress, UUID> {

    List<UserTopicProgress> findByUserId(UUID userId);

    Optional<UserTopicProgress> findByUserIdAndTopicId(UUID userId, UUID topicId);

    List<UserTopicProgress> findByUserIdAndTopicCourseVersionId(UUID userId, UUID courseVersionId);

    @EntityGraph(attributePaths = {"topic", "topic.courseVersion"})
    @Query(
            "SELECT tp FROM UserTopicProgress tp "
                    + "WHERE tp.user.id = :userId "
                    + "AND tp.status = com.signasource.signa_api.learning.entity.ProgressStatus.IN_PROGRESS "
                    + "AND tp.topic.courseVersion.id IN :versionIds "
                    + "ORDER BY tp.topic.courseVersion.id, tp.topic.order")
    List<UserTopicProgress> findInProgressTopics(
            @Param("userId") UUID userId, @Param("versionIds") Collection<UUID> versionIds);
}
