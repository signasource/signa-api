package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.UserBlockProgress;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserBlockProgressRepository extends JpaRepository<UserBlockProgress, UUID> {

    boolean existsByUserIdAndLessonBlockId(UUID userId, UUID lessonBlockId);

    long countByUserIdAndLessonBlockLessonId(UUID userId, UUID lessonId);
}
