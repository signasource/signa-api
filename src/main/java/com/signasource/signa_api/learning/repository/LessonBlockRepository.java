package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.LessonBlock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LessonBlockRepository extends JpaRepository<LessonBlock, UUID> {

    List<LessonBlock> findByLessonIdOrderByOrderAsc(UUID lessonId);

    @EntityGraph(attributePaths = {"lesson.topic.courseVersion"})
    Optional<LessonBlock> findWithCourseVersionById(UUID id);
}
