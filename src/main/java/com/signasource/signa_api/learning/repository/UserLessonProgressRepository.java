package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.UserLessonProgress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserLessonProgressRepository extends JpaRepository<UserLessonProgress, Long> {

    List<UserLessonProgress> findByUserId(Long userId);

    Optional<UserLessonProgress> findByUserIdAndLessonId(Long userId, Long lessonId);

    List<UserLessonProgress> findByUserIdAndLessonTopicId(Long userId, Long topicId);
}
