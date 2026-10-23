package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.PracticeAttempt;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PracticeAttemptRepository extends JpaRepository<PracticeAttempt, UUID> {

    void deleteByLessonBlockId(UUID lessonBlockId);

    long countByUserId(UUID userId);

    long countByUserIdAndIsCorrectTrueAndLessonBlockTypeIn(
            UUID userId, Collection<BlockType> types);

    List<PracticeAttempt> findByUserIdOrderByAttemptedAtDesc(UUID userId);
}
