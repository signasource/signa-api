package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.VersionStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CourseVersionRepository extends JpaRepository<CourseVersion, UUID> {

    @EntityGraph(attributePaths = {"course", "topics"})
    Optional<CourseVersion> findByCourseIdAndStatus(UUID courseId, VersionStatus status);

    @EntityGraph(attributePaths = {"course"})
    List<CourseVersion> findByCourseIdInAndStatus(Collection<UUID> courseIds, VersionStatus status);

    @EntityGraph(attributePaths = {"course"})
    List<CourseVersion> findAllByOrderByStatusAscUpdatedAtDesc();

    // Only eager-load course + topics; lessons/blocks are batch-fetched lazily (batch_fetch_size)
    // to avoid MultipleBagFetchException in Hibernate 6 with nested List collections.
    @EntityGraph(attributePaths = {"course", "topics"})
    Optional<CourseVersion> findWithFullTreeById(UUID id);

    @Query("SELECT v FROM CourseVersion v JOIN FETCH v.course WHERE v.course.id = :courseId")
    List<CourseVersion> findByCourseId(@Param("courseId") UUID courseId);
}
