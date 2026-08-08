package com.signasource.signa_api.learning.repository;

import com.signasource.signa_api.learning.entity.LessonBlock;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface LessonBlockRepository extends JpaRepository<LessonBlock, UUID> {

    List<LessonBlock> findByLessonIdOrderByOrderAsc(UUID lessonId);

    long countByLessonId(UUID lessonId);

    @Query("select coalesce(sum(b.xpReward), 0) from LessonBlock b where b.lesson.id = :lessonId")
    int sumXpRewardByLessonId(UUID lessonId);
}
