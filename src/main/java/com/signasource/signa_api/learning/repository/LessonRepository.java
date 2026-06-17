package com.signasource.signa_api.learning.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.signasource.signa_api.learning.entity.Lesson;

@Repository
public interface LessonRepository extends JpaRepository<Lesson, UUID> {

	List<Lesson> findByTopicIdOrderByOrderAsc(UUID topicId);
}
