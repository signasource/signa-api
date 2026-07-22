package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.UserLessonProgress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLessonProgressRepository extends JpaRepository<UserLessonProgress, UUID> {

    List<UserLessonProgress> findByUserId(UUID userId);

    Optional<UserLessonProgress> findByUserIdAndLessonId(UUID userId, UUID lessonId);

    List<UserLessonProgress> findByUserIdAndLessonTopicId(UUID userId, UUID topicId);
}
