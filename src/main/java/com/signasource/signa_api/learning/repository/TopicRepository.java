package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.repository.projection.TopicLessonTotalView;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TopicRepository extends JpaRepository<Topic, UUID> {

    List<Topic> findByCourseVersionIdOrderByOrderAsc(UUID courseVersionId);

    @Query(
            "SELECT DISTINCT t FROM Topic t LEFT JOIN FETCH t.lessons "
                    + "WHERE t.courseVersion.id = :versionId "
                    + "ORDER BY t.order ASC")
    List<Topic> findRoadmapTopics(@Param("versionId") UUID versionId);

    @Query(
            "SELECT t.courseVersion.id AS courseVersionId, t.id AS topicId, "
                    + "COUNT(l.id) AS totalLessons "
                    + "FROM Topic t LEFT JOIN t.lessons l "
                    + "WHERE t.courseVersion.id IN :versionIds "
                    + "GROUP BY t.courseVersion.id, t.id")
    List<TopicLessonTotalView> findTopicLessonTotals(
            @Param("versionIds") Collection<UUID> versionIds);
}
