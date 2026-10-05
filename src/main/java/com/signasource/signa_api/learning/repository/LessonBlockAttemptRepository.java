package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.LessonBlockAttempt;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LessonBlockAttemptRepository extends JpaRepository<LessonBlockAttempt, UUID> {

    void deleteByLessonBlockId(UUID lessonBlockId);

    List<LessonBlockAttempt> findByUserIdAndLessonBlockId(UUID userId, UUID lessonBlockId);

    long countByUserIdAndLessonBlockId(UUID userId, UUID lessonBlockId);

    /** Exercise attempts only: INFO views are stored with a null correctness. */
    long countByUserIdAndIsCorrectIsNotNull(UUID userId);

    boolean existsByUserIdAndLessonBlockId(UUID userId, UUID lessonBlockId);

    boolean existsByUserIdAndLessonBlockIdAndIsCorrectTrue(UUID userId, UUID lessonBlockId);

    long countByUserIdAndIsCorrectTrueAndLessonBlockTypeIn(
            UUID userId, Collection<BlockType> types);

    boolean existsByUserIdAndLessonBlockLessonIdAndIsCorrectFalse(UUID userId, UUID lessonId);

    List<LessonBlockAttempt> findByUserIdOrderByAttemptedAtDesc(UUID userId);
}
