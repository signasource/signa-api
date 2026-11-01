package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.repository.projection.LessonBlockAggregateView;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LessonBlockRepository extends JpaRepository<LessonBlock, UUID> {

    List<LessonBlock> findByLessonIdOrderByOrderAsc(UUID lessonId);

    @EntityGraph(attributePaths = {"lesson.topic.courseVersion"})
    Optional<LessonBlock> findWithCourseVersionById(UUID id);

    @Query(
            "SELECT b.lesson.id AS lessonId, COUNT(b.id) AS blockCount, "
                    + "COALESCE(SUM(b.xpReward), 0) AS xpTotal "
                    + "FROM LessonBlock b "
                    + "WHERE b.lesson.topic.courseVersion.id = :versionId "
                    + "GROUP BY b.lesson.id")
    List<LessonBlockAggregateView> aggregateByCourseVersionId(@Param("versionId") UUID versionId);

    @Query(
            "SELECT b FROM LessonBlock b JOIN FETCH b.lesson "
                    + "WHERE b.lesson.topic.courseVersion.id = :versionId")
    List<LessonBlock> findByCourseVersionId(@Param("versionId") UUID versionId);

    @Query(
            "SELECT b FROM LessonBlock b JOIN FETCH b.lesson "
                    + "WHERE b.lesson.topic.courseVersion.id IN :versionIds")
    List<LessonBlock> findByLessonTopicCourseVersionIdIn(
            @Param("versionIds") List<UUID> versionIds);

    /** The signs a course version teaches, in the order a learner meets them. */
    @Query(
            "SELECT b FROM LessonBlock b JOIN FETCH b.lesson l JOIN FETCH l.topic t "
                    + "WHERE t.courseVersion.id = :versionId "
                    + "AND b.type = com.signasource.signa_api.learning.entity.BlockType.INTRODUCE_SIGN "
                    + "ORDER BY t.order, l.order, b.order")
    List<LessonBlock> findIntroducedSigns(@Param("versionId") UUID versionId);
}
